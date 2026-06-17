package com.kododake.aabrowser.web

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import com.kododake.aabrowser.data.BrowserPreferences
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Network-level ad / tracker blocker for the in-app WebView.
 *
 * It works by inspecting every sub-resource the page tries to load (via
 * [android.webkit.WebViewClient.shouldInterceptRequest]) and returning an empty
 * response for hosts that belong to known ad / tracking networks. This is the
 * only reliable way to block ads inside a system WebView (there is no extension
 * API like uBlock Origin).
 *
 * Blocklist sources, merged together:
 *  1. A small hard-coded core list ([BUILTIN_HOSTS]) so blocking works even if
 *     the bundled asset is missing.
 *  2. An optional `assets/adblock_hosts.txt` file (hosts-file or plain-domain
 *     format) that can be grown over time without touching code.
 *
 * Limitation worth knowing: host-based blocking cannot remove YouTube in-stream
 * video ads, because YouTube serves those ads from the same domains
 * (`*.googlevideo.com`) as the actual video. It does remove the vast majority of
 * banner / display / pop-up / tracker requests across the rest of the web.
 */
object AdBlocker {

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var loaded: Boolean = false

    /** Exact hosts that should be blocked (e.g. "doubleclick.net"). */
    private val blockedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val EMPTY_RESPONSE_BYTES = ByteArray(0)

    /**
     * Initialise the blocker. Safe to call repeatedly / from any thread. The
     * blocklist asset is parsed once on a background thread the first time.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        enabled = BrowserPreferences.isAdBlockEnabled(context)
        if (loaded) {
            // Already initialised this process; just check if the remote list
            // is due for a refresh (cheap, internally rate-limited).
            FilterListUpdater.maybeUpdate(appContext)
            return
        }
        synchronized(this) {
            if (loaded) return
            // Seed the hard-coded core list immediately so blocking is active
            // even before the (larger) lists finish parsing.
            blockedHosts.addAll(BUILTIN_HOSTS)
            loaded = true
        }
        Thread {
            // 1) bundled asset, 2) cached AdGuard list from a previous fetch,
            // 3) trigger a network refresh if the cache is missing/stale.
            runCatching { loadHostsFromAsset(appContext) }
            runCatching { FilterListUpdater.loadCached(appContext) }
            FilterListUpdater.maybeUpdate(appContext)
        }.apply { isDaemon = true }.start()
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        BrowserPreferences.setAdBlockEnabled(context, value)
    }

    /** How many distinct hosts are currently in the blocklist. */
    fun blockedHostCount(): Int = blockedHosts.size

    /**
     * Decide whether a request URL should be blocked. Cheap enough to run on the
     * WebView's resource-loading thread for every sub-resource.
     */
    fun shouldBlock(url: String?): Boolean {
        if (!enabled || url.isNullOrEmpty()) return false
        // Safe ad/telemetry endpoints that live on otherwise-allowed hosts
        // (e.g. YouTube's own domain). Blocking the whole host would break the
        // site, so we only block these specific request paths.
        val lowerUrl = url.lowercase()
        if (BLOCKED_URL_FRAGMENTS.any { lowerUrl.contains(it) }) return true
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return isHostBlocked(host)
    }

    /** True for YouTube watch pages where the ad-skip script should run. */
    fun isYouTube(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }

    /**
     * Matches the exact host and every parent domain, so "ads.tracker.co.uk"
     * is blocked when the list contains "tracker.co.uk".
     */
    private fun isHostBlocked(host: String): Boolean {
        if (blockedHosts.contains(host)) return true
        var index = host.indexOf('.')
        while (index in 0 until host.length - 1) {
            val parent = host.substring(index + 1)
            if (blockedHosts.contains(parent)) return true
            index = host.indexOf('.', index + 1)
        }
        return false
    }

    /** An empty 204-style response used to satisfy blocked requests. */
    fun blockedResponse(): WebResourceResponse {
        val response = WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(EMPTY_RESPONSE_BYTES)
        )
        response.setStatusCodeAndReasonPhrase(204, "No Content")
        response.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        return response
    }

    private fun loadHostsFromAsset(context: Context) {
        context.assets.open(ASSET_FILE_NAME).bufferedReader().use { reader ->
            parseInto(reader, blockedHosts)
        }
    }

    /**
     * Parse a filter list (hosts or AdGuard/ABP syntax) directly into the live
     * blocklist. Returns the number of hosts added. Used by [FilterListUpdater]
     * for the cached + freshly downloaded AdGuard list.
     */
    fun ingest(reader: BufferedReader): Int {
        val before = blockedHosts.size
        parseInto(reader, blockedHosts)
        return blockedHosts.size - before
    }

    private val WHITESPACE = Regex("\\s+")
    // matches() requires the whole string to match, so no anchors are needed.
    private val PLAIN_DOMAIN = Regex("[a-z0-9._-]+")

    /**
     * Understands two formats so we can consume both our bundled hosts file and
     * AdGuard's published filter lists:
     *  - hosts format:  "0.0.0.0 domain", "127.0.0.1 domain", or bare "domain"
     *  - AdGuard/ABP network rules:  "||domain.tld^"
     *
     * Anything we can't safely turn into a whole-domain block is skipped:
     * exceptions (@@), cosmetic/scriptlet rules (##, #%#, #$#), and any rule
     * carrying modifiers ($...), paths (/), or wildcards (*). The AdGuard DNS
     * filter is overwhelmingly plain "||domain^" rules, which map cleanly here.
     */
    private fun parseInto(reader: BufferedReader, into: MutableSet<String>) {
        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEachLine
            // Skip ABP exceptions and cosmetic/scriptlet rules we can't apply.
            if (line.startsWith("@@")) return@forEachLine
            if (line.contains("##") || line.contains("#@#") ||
                line.contains("#%#") || line.contains("#\$#")) return@forEachLine

            val candidate: String = if (line.startsWith("||")) {
                // AdGuard/ABP network rule. Only accept a clean "||domain^".
                if (line.contains('$') || line.contains('/') || line.contains('*')) return@forEachLine
                line.removePrefix("||").trimEnd('^')
            } else {
                // hosts format
                val parts = line.split(WHITESPACE)
                when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 -> parts[0]
                    else -> return@forEachLine
                }
            }

            val host = candidate.removePrefix("www.").lowercase()
            if (host.isNotEmpty() && host != "localhost" &&
                host.contains('.') && PLAIN_DOMAIN.matches(host)) {
                into.add(host)
            }
        }
    }

    private const val ASSET_FILE_NAME = "adblock_hosts.txt"

    /**
     * URL path fragments that are blocked regardless of host. These are
     * ad/telemetry endpoints served from otherwise-needed domains (notably
     * YouTube), so we cannot block them by host without breaking the site.
     */
    // Only clearly ad-specific endpoints. We deliberately do NOT block YouTube
    // playback telemetry like /api/stats/qoe or /csi_204 — those are not ads and
    // blocking them could interfere with video playback (our main scope).
    private val BLOCKED_URL_FRAGMENTS: List<String> = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/get_midroll_",
        "googleads.g.doubleclick.net"
    )

    /**
     * YouTube ad handling. Host-based blocking cannot stop YouTube in-stream
     * video ads (they come from the same servers as the video), so instead we
     * inject a script that:
     *   - clicks the "Skip" button as soon as it appears,
     *   - fast-forwards un-skippable ads to their end and mutes them,
     *   - removes banner / overlay / promoted ad elements.
     * It uses a MutationObserver plus a short interval so it keeps working as
     * YouTube navigates between videos without a full page reload.
     *
     * This is the most reliable in-WebView technique available today; Google
     * actively changes YouTube, so it may need occasional selector updates.
     */
    val YOUTUBE_AD_SKIP_JS: String = """
        (function() {
            if (window.__aabYtAdSkip) return;
            window.__aabYtAdSkip = true;
            function handle() {
                try {
                    var player = document.getElementById('movie_player') ||
                        document.querySelector('.html5-video-player');
                    // Target the player's OWN video element, not some other <video>
                    // on the page (e.g. homepage hover previews).
                    var video = (player && player.querySelector('video')) ||
                        document.querySelector('video');
                    var skipBtn = document.querySelector(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
                        '.ytp-skip-ad-button, .ytp-ad-skip-button-container button'
                    );
                    if (skipBtn) { skipBtn.click(); }
                    var adShowing = player && player.classList &&
                        (player.classList.contains('ad-showing') ||
                         player.classList.contains('ad-interrupting'));
                    if (adShowing && video) {
                        // Remember the user's real mute state ONCE, before we touch it,
                        // so the actual video is never left muted after the ad ends.
                        if (!video.dataset.aabMutedSaved) {
                            video.dataset.aabMutedSaved = '1';
                            video.dataset.aabPrevMuted = video.muted ? '1' : '0';
                        }
                        if (!isNaN(video.duration) && isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                        video.muted = true;
                        var p = video.play();
                        if (p && p.catch) { p.catch(function(){}); }
                    } else if (video && video.dataset.aabMutedSaved) {
                        // Ad finished: restore exactly the mute state we saved.
                        video.muted = (video.dataset.aabPrevMuted === '1');
                        delete video.dataset.aabMutedSaved;
                        delete video.dataset.aabPrevMuted;
                    }
                    var overlayClose = document.querySelector(
                        '.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container'
                    );
                    if (overlayClose) { overlayClose.click(); }
                    var adSelectors = [
                        '.ytp-ad-overlay-slot', '#player-ads', '#masthead-ad',
                        'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer',
                        'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer',
                        '.ytd-companion-slot-renderer', 'ytd-banner-promo-renderer'
                    ];
                    adSelectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(e) { e.remove(); });
                    });
                } catch (e) {}
            }
            try {
                var obs = new MutationObserver(handle);
                obs.observe(document.documentElement || document.body,
                    { childList: true, subtree: true });
            } catch (e) {}
            setInterval(handle, 400);
            handle();
        })();
    """.trimIndent()

    /**
     * Conservative cosmetic filtering: hides the empty containers that remain
     * after the ad request itself was blocked. Selectors are deliberately
     * specific (ad-network class/id conventions) to avoid hiding real content.
     */
    val COSMETIC_JS: String = """
        (function() {
            var id = 'aabrowser-adblock-style';
            if (document.getElementById(id)) return;
            var css = [
                'ins.adsbygoogle',
                '.adsbygoogle',
                '[id^="google_ads_"]',
                '[id^="div-gpt-ad"]',
                '[id^="gpt-ad"]',
                '[id^="ad-slot"]',
                '[class~="advertisement"]',
                '[class~="ad-banner"]',
                '[class~="ad-container"]',
                '[aria-label="Advertisement"]',
                'iframe[src*="doubleclick.net"]',
                'iframe[src*="googlesyndication.com"]',
                'iframe[src*="adservice.google"]',
                'iframe[src*="amazon-adsystem.com"]',
                'iframe[src*="taboola.com"]',
                'iframe[src*="outbrain.com"]'
            ].join(',') + '{display:none !important;height:0 !important;min-height:0 !important;}';
            var style = document.createElement('style');
            style.id = id;
            style.type = 'text/css';
            style.appendChild(document.createTextNode(css));
            (document.head || document.documentElement).appendChild(style);
        })();
    """.trimIndent()

    /**
     * Always-on core blocklist (high-impact ad & tracking networks). Kept small
     * and hard-coded so the feature degrades gracefully without the asset file.
     */
    private val BUILTIN_HOSTS: Set<String> = setOf(
        // Google ads / measurement
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "partner.googleadservices.com",
        "analytics.google.com",
        // Common ad exchanges / networks
        "adnxs.com",
        "adsrvr.org",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "casalemedia.com",
        "33across.com",
        "taboola.com",
        "outbrain.com",
        "adform.net",
        "smartadserver.com",
        "yieldmo.com",
        "sharethrough.com",
        "indexww.com",
        "bidswitch.net",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "amazon-adsystem.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "inmobi.com",
        "mopub.com",
        "chartboost.com",
        "zedo.com",
        "media.net",
        "revcontent.com",
        "mgid.com",
        "propellerads.com",
        "popads.net",
        "popcash.net",
        "exoclick.com",
        "adsterra.com",
        // Trackers / analytics
        "hotjar.com",
        "mixpanel.com",
        "segment.com",
        "segment.io",
        "amplitude.com",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "newrelic.com",
        "nr-data.net",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "kochava.com",
        "bugsnag.com",
        "optimizely.com",
        "bluekai.com",
        "bkrtx.com",
        "demdex.net",
        "everesttech.net",
        "krxd.net",
        "agkn.com",
        "rlcdn.com",
        "crwdcntrl.net",
        "exelator.com",
        // Social trackers
        "connect.facebook.net",
        "pixel.facebook.com",
        "ads-twitter.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "ads.linkedin.com",
        "ads.pinterest.com",
        "ads.yahoo.com",
        "advertising.com"
    )
}
