package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.webkit.WebResourceResponse
import com.kododake.aabrowser.BuildConfig
import com.kododake.aabrowser.data.BrowserPreferences
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Android/WebView adapter around the uBlock-style [FilterEngine]. */
object AdBlocker {
    private const val FILTERS_ASSET = "adblock/blocklist.txt"
    private const val CACHE_MAGIC = 0x41414246
    private const val CACHE_VERSION = 4
    private const val MAX_BRIDGE_TOKENS = 512
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024

    @Volatile
    private var engine = FilterEngine.parse(emptySequence())

    @Volatile
    private var loaded = false

    val isLoaded: Boolean
        get() = loaded

    private var loading = false
    private val readyCallbacks = ArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "adblock-filter-loader")
    }

    private val sessionBlockCount = AtomicLong(0)

    /** Hosts the user switched Shields off for; read on every request, so it is an immutable snapshot. */
    @Volatile
    private var siteAllowlist: Set<String> = emptySet()

    /** Re-reads the per-site allowlist from preferences (cheap; call after the user toggles a site). */
    fun refreshSiteAllowlist(context: Context) {
        siteAllowlist = BrowserPreferences.getShieldsDisabledHosts(context)
    }

    fun isSiteAllowlisted(host: String?): Boolean = FilterEngine.hostWithinAny(host, siteAllowlist)

    val blockedThisSession: Long
        get() = sessionBlockCount.get()

    fun resetSessionCount() {
        sessionBlockCount.set(0)
    }

    /** Starts parsing the static filter lists off the main thread. */
    fun ensureLoadedAsync(context: Context) {
        runWhenLoaded(context) {}
    }

    /**
     * Runs [action] on the main thread once all filters are compiled.
     * Navigation uses this to ensure the first request cannot bypass Shields.
     */
    fun runWhenLoaded(context: Context, action: () -> Unit) {
        if (loaded) {
            runOnMainThread(action)
            return
        }

        val appContext = context.applicationContext
        var startLoader = false
        var runImmediately = false
        synchronized(this) {
            if (loaded) {
                runImmediately = true
            } else {
                readyCallbacks += action
                if (!loading) {
                    loading = true
                    startLoader = true
                }
            }
        }
        if (runImmediately) {
            runOnMainThread(action)
            return
        }
        if (!startLoader) return

        loader.execute {
            refreshSiteAllowlist(appContext)
            val replacement = loadEngine(appContext)
            val callbacks: List<() -> Unit>
            synchronized(this) {
                engine = replacement
                loaded = true
                loading = false
                callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
            }
            mainHandler.post { callbacks.forEach { it() } }
            FilterListUpdateWorker.schedulePeriodic(appContext)
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    /** Rebuilds the immutable engine after subscription or cache changes. */
    fun reload(context: Context) {
        refreshSiteAllowlist(context.applicationContext)
        val replacement = loadEngine(context.applicationContext)
        synchronized(this) {
            engine = replacement
            loaded = true
        }
    }

    private fun loadEngine(context: Context): FilterEngine {
        val bundledSize = runCatching { context.assets.open(FILTERS_ASSET).use { it.available() } }.getOrDefault(-1)
        val fingerprint = "${BuildConfig.VERSION_CODE}:${BuildConfig.VERSION_NAME}:$bundledSize:${RemoteFilterListManager.cacheFingerprint(context)}"
        readCachedEngine(context, fingerprint)?.let { return it }
        val replacement = runCatching {
            FilterEngine.parseSources(sequence {
                context.assets.open(FILTERS_ASSET).bufferedReader().use { reader ->
                    while (true) yield(FilterEngine.SourceLine(reader.readLine() ?: break, trusted = true))
                }
                yieldAll(RemoteFilterListManager.cachedFilterLines(context))
            })
        }.getOrElse {
            FilterEngine.parseSources(context.assets.open(FILTERS_ASSET).bufferedReader().use { reader ->
                reader.readLines().asSequence().map { FilterEngine.SourceLine(it, trusted = true) }
            })
        }
        writeCachedEngine(context, fingerprint, replacement)
        return replacement
    }

    private fun readCachedEngine(context: Context, fingerprint: String): FilterEngine? = runCatching {
        val file = engineCacheFile(context)
        deleteStaleCaches(file)
        if (!file.isFile || file.length() !in 1..MAX_CACHE_BYTES) return@runCatching null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION ||
                input.readUTF() != fingerprint) return@use null
            FilterEngine.readSnapshot(input)
        }
    }.getOrNull()

    private fun writeCachedEngine(context: Context, fingerprint: String, engine: FilterEngine) {
        runCatching {
            val target = engineCacheFile(context)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            try {
                DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
                    output.writeInt(CACHE_MAGIC)
                    output.writeInt(CACHE_VERSION)
                    output.writeUTF(fingerprint)
                    engine.writeSnapshot(output)
                }
                runCatching {
                    java.nio.file.Files.move(
                        temporary.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                }.getOrElse {
                    java.nio.file.Files.move(
                        temporary.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun engineCacheFile(context: Context) =
        File(context.filesDir, "adblock/compiled-engine-$CACHE_VERSION.bin")

    /** Snapshots written by earlier app versions use an older format; free the space. */
    private fun deleteStaleCaches(current: File) {
        current.parentFile?.listFiles()?.forEach { file ->
            if (file != current && file.name.startsWith("compiled-engine-") && file.name.endsWith(".bin")) file.delete()
        }
    }

    fun scriptletInvocations(pageUrl: String?): List<FilterEngine.ScriptletInvocation> {
        val current = engine
        if (isSiteAllowlisted(FilterEngine.hostOf(pageUrl)) || current.pagePolicy(pageUrl).document) return emptyList()
        return current.scriptletsFor(pageUrl)
    }

    /** Content exceptions that apply to the page at [pageUrl]. */
    fun pagePolicy(pageUrl: String?): FilterEngine.PagePolicy = engine.pagePolicy(pageUrl)

    fun interceptOrNull(
        requestUrl: Uri?,
        pageUrl: String?,
        pageHost: String? = null,
        isMainFrame: Boolean,
        requestHeaders: Map<String, String> = emptyMap(),
        lenient: Boolean = false
    ): WebResourceResponse? {
        if (isMainFrame || requestUrl == null) return null
        val scheme = requestUrl.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val current = engine
        // With no page context (service worker) the request's own site stands in for the page.
        val siteHost = pageHost ?: FilterEngine.hostOf(pageUrl) ?: requestUrl.host
        if (isSiteAllowlisted(siteHost)) return null
        if (pageUrl != null && current.pagePolicy(pageUrl).document) return null
        val type = inferResourceType(requestUrl, requestHeaders)
        if (!current.shouldBlock(FilterEngine.Request(
                url = requestUrl.toString(),
                pageUrl = pageUrl,
                resourceType = type,
                requestHost = requestUrl.host,
                pageHost = pageHost
            ), lenientExceptions = lenient)) return null

        sessionBlockCount.incrementAndGet()
        return blockedResponse(type)
    }

    /**
     * A harmless stand-in for the blocked resource, typed so the page does not choke on it: an
     * empty script/stylesheet, a transparent 1x1 GIF for images, and 204 No Content for fetches.
     * Never cached, so unblocking a site takes effect on the next load.
     */
    internal fun blockedResponse(type: FilterEngine.ResourceType): WebResourceResponse {
        val headers = mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*")
        return when (type) {
            FilterEngine.ResourceType.SCRIPT ->
                WebResourceResponse("text/javascript", "utf-8", 200, "OK", headers, ByteArrayInputStream(ByteArray(0)))
            FilterEngine.ResourceType.STYLESHEET ->
                WebResourceResponse("text/css", "utf-8", 200, "OK", headers, ByteArrayInputStream(ByteArray(0)))
            FilterEngine.ResourceType.IMAGE ->
                WebResourceResponse("image/gif", null, 200, "OK", headers, ByteArrayInputStream(TRANSPARENT_GIF))
            else ->
                WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers, ByteArrayInputStream(ByteArray(0)))
        }
    }

    private val TRANSPARENT_GIF = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C,
        0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    )

    /**
     * JSON for the document-start cosmetic script: `{"s":[...],"o":[...],"g":true}` with the site's
     * specific hide selectors, the un-keyed generic selectors, and whether keyed generic rules
     * should be requested; an empty string when nothing applies to [pageUrl].
     */
    fun cosmeticInitJson(pageUrl: String?): String {
        val current = engine
        if (isSiteAllowlisted(FilterEngine.hostOf(pageUrl))) return ""
        val policy = current.pagePolicy(pageUrl)
        if (policy.hidesNothing) return ""
        val init = current.cosmeticInit(pageUrl, allowGeneric = policy.allowGeneric, allowSpecific = policy.allowSpecific) ?: return ""
        return org.json.JSONObject()
            .put("s", org.json.JSONArray(init.specific))
            .put("o", org.json.JSONArray(init.other))
            .put("g", init.generic)
            .toString()
    }

    /**
     * JSON array of generic hide selectors keyed by the given page classes/ids (JSON arrays of
     * strings, capped so a hostile page cannot make the bridge do unbounded work).
     */
    fun genericSelectorsJson(pageUrl: String?, classesJson: String?, idsJson: String?): String {
        val host = FilterEngine.hostOf(pageUrl) ?: return ""
        val classes = parseTokenArray(classesJson)
        val ids = parseTokenArray(idsJson)
        if (classes.isEmpty() && ids.isEmpty()) return ""
        val current = engine
        if (isSiteAllowlisted(host) || !current.pagePolicy(pageUrl).allowGeneric) return ""
        val selectors = current.genericSelectorsFor(classes, ids, host)
        return if (selectors.isEmpty()) "" else org.json.JSONArray(selectors).toString()
    }

    private fun parseTokenArray(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>(minOf(array.length(), MAX_BRIDGE_TOKENS))
        for (index in 0 until minOf(array.length(), MAX_BRIDGE_TOKENS)) {
            val token = array.optString(index, "")
            if (token.isNotEmpty() && token.length <= 128) out += token
        }
        return out
    }

    /**
     * WebView does not say what kind of resource a request is for, so it is inferred: the
     * `Sec-Fetch-Dest` header when Chromium exposes it, then the URL's extension, then the
     * `Accept` header, and finally a wildcard Accept plus an `Origin` header is taken as a fetch/XHR.
     */
    internal fun inferResourceType(uri: Uri, headers: Map<String, String>): FilterEngine.ResourceType =
        inferResourceType(uri.path.orEmpty(), headers)

    internal fun inferResourceType(path: String, headers: Map<String, String>): FilterEngine.ResourceType {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
        when (header("Sec-Fetch-Dest").lowercase(Locale.ROOT)) {
            "script", "worker", "sharedworker", "serviceworker", "audioworklet", "paintworklet" -> return FilterEngine.ResourceType.SCRIPT
            "image" -> return FilterEngine.ResourceType.IMAGE
            "style" -> return FilterEngine.ResourceType.STYLESHEET
            "font" -> return FilterEngine.ResourceType.FONT
            "audio", "video", "track" -> return FilterEngine.ResourceType.MEDIA
            "iframe", "frame", "fencedframe", "embed", "object" -> return FilterEngine.ResourceType.SUBDOCUMENT
            "empty" -> return FilterEngine.ResourceType.XHR
            "manifest", "report", "xslt" -> return FilterEngine.ResourceType.OTHER
        }
        when {
            path.endsWith(".css", ignoreCase = true) -> return FilterEngine.ResourceType.STYLESHEET
            path.endsWithAny(SCRIPT_EXTENSIONS) -> return FilterEngine.ResourceType.SCRIPT
            path.endsWithAny(IMAGE_EXTENSIONS) -> return FilterEngine.ResourceType.IMAGE
            path.endsWithAny(FONT_EXTENSIONS) -> return FilterEngine.ResourceType.FONT
            path.endsWithAny(MEDIA_EXTENSIONS) -> return FilterEngine.ResourceType.MEDIA
            path.endsWithAny(DATA_EXTENSIONS) -> return FilterEngine.ResourceType.XHR
        }
        val accept = header("Accept")
        return when {
            accept.contains("text/css", ignoreCase = true) -> FilterEngine.ResourceType.STYLESHEET
            accept.contains("image/", ignoreCase = true) -> FilterEngine.ResourceType.IMAGE
            accept.contains("font/", ignoreCase = true) -> FilterEngine.ResourceType.FONT
            accept.contains("audio/", ignoreCase = true) || accept.contains("video/", ignoreCase = true) -> FilterEngine.ResourceType.MEDIA
            accept.contains("javascript", ignoreCase = true) -> FilterEngine.ResourceType.SCRIPT
            accept.contains("application/json", ignoreCase = true) || accept.contains("text/event-stream", ignoreCase = true) -> FilterEngine.ResourceType.XHR
            accept.contains("text/html", ignoreCase = true) -> FilterEngine.ResourceType.SUBDOCUMENT
            accept.trim() == "*/*" && header("Origin").isNotEmpty() -> FilterEngine.ResourceType.XHR
            else -> FilterEngine.ResourceType.OTHER
        }
    }

    private fun String.endsWithAny(suffixes: Array<String>): Boolean =
        suffixes.any { endsWith(it, ignoreCase = true) }

    private val IMAGE_EXTENSIONS = arrayOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".avif", ".ico")
    private val FONT_EXTENSIONS = arrayOf(".woff", ".woff2", ".ttf", ".otf", ".eot")
    private val MEDIA_EXTENSIONS = arrayOf(".mp3", ".mp4", ".webm", ".m3u8", ".ts", ".ogg", ".wav")
    private val SCRIPT_EXTENSIONS = arrayOf(".js", ".mjs")
    private val DATA_EXTENSIONS = arrayOf(".json", ".xml")
}
