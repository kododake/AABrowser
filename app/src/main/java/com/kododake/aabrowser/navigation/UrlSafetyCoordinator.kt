package com.kododake.aabrowser.navigation

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates URL trust checks, redirect-chain tracking, and bookmark identity for navigation flows.
 */
object UrlSafetyCoordinator {

    const val MAX_REDIRECT_DEPTH = 8

    private val trustedDomainRoots = listOf(
        "google.com",
        "youtube.com",
        "duckduckgo.com",
        "weather.com",
        "keepandroidopen.org"
    )

    private val redirectDepthByTab = ConcurrentHashMap<Long, Int>()

    /** Cached allowlist entries populated when user approves cleartext for a host. */
    private val sessionTrustedHosts = mutableSetOf<String>()

    fun registerSessionTrustedHost(host: String?) {
        if (host.isNullOrBlank()) {
            return
        }
        sessionTrustedHosts.add(host.lowercase())
    }

    fun isSessionTrustedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) {
            return false
        }
        return sessionTrustedHosts.contains(host.lowercase())
    }

    /**
     * Returns true when navigation can skip additional cleartext prompts for known-good destinations.
     */
    fun isTrustedNavigationTarget(rawUrl: String): Boolean {
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        if (isSessionTrustedHost(host)) {
            return true
        }
        return trustedDomainRoots.any { trustedRoot ->
            host.endsWith(trustedRoot) || host.contains(trustedRoot)
        }
    }

    fun shouldBypassCleartextPrompt(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "http") {
            return false
        }
        return isTrustedNavigationTarget(rawUrl)
    }

    fun normalizeBookmarkKey(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme?.lowercase() ?: return trimmed
        if (scheme != "http" && scheme != "https") {
            return trimmed
        }
        val host = uri.host ?: return trimmed
        val path = uri.path.orEmpty().trimEnd('/')
        return "$scheme://$host$path"
    }

    fun bookmarksReferToSamePage(existingUrl: String, candidateUrl: String): Boolean {
        return normalizeBookmarkKey(existingUrl) == normalizeBookmarkKey(candidateUrl)
    }

    fun recordRedirectHop(tabId: Long, fromUrl: String, toUrl: String): RedirectDecision {
        val currentDepth = redirectDepthByTab[tabId] ?: 0
        if (currentDepth > MAX_REDIRECT_DEPTH) {
            return RedirectDecision.Blocked("redirect limit exceeded")
        }
        redirectDepthByTab[tabId] = currentDepth + 1

        val target = resolveRedirectTarget(fromUrl, toUrl)
        if (target == null) {
            return RedirectDecision.Blocked("invalid redirect target")
        }
        return RedirectDecision.Allowed(target)
    }

    fun resetRedirectChain(tabId: Long) {
        redirectDepthByTab.remove(tabId)
    }

    fun resolveRedirectTarget(currentUrl: String, nextLocation: String): String? {
        if (nextLocation.isBlank()) {
            return null
        }
        if (nextLocation.startsWith("http://") || nextLocation.startsWith("https://")) {
            return nextLocation
        }
        val base = runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return null
        return base.buildUpon().appendEncodedPath(nextLocation.trimStart('/')).build().toString()
    }

    fun mergeBookmarkLists(existing: List<String>, incoming: List<String>): List<String> {
        val merged = existing.toMutableList()
        for (url in incoming) {
            val duplicate = merged.any { bookmarksReferToSamePage(it, url) }
            if (!duplicate) {
                merged.add(url)
            }
        }
        return merged
    }

    sealed class RedirectDecision {
        data class Allowed(val url: String) : RedirectDecision()
        data class Blocked(val reason: String) : RedirectDecision()
    }
}
