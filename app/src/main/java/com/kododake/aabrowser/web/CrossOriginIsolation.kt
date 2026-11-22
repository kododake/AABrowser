/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.web

import android.content.Context
import android.net.Uri
import android.os.LocaleList
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.data.BrowserPreferences
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * WebView ignores COOP/COEP, so sites that need cross-origin isolation (SharedArrayBuffer,
 * wasm threads) break. When the user enables it, origins whose pages send COOP same-origin +
 * COEP are allowlisted on the profile, and documents of those origins that send the headers
 * are served with Document-Isolation-Policy.
 */
object CrossOriginIsolation {
    private const val PREFS = "cross_origin_isolation"
    private const val KEY_ORIGINS = "origins"
    private const val DIP_HEADER = "Document-Isolation-Policy"

    @Volatile
    private var enabled = false
    @Volatile
    private var origins: Set<String> = emptySet()
    @Volatile
    private var loaded = false
    @Volatile
    private var swClientInstalled = false
    private val probed = HashSet<String>()
    private val probeExecutor = Executors.newSingleThreadExecutor()

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            enabled = BrowserPreferences.isCrossOriginIsolationEnabled(context)
            origins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_ORIGINS, emptySet()).orEmpty().toSet()
            loaded = true
        }
    }

    private fun store(context: Context, value: Set<String>) {
        synchronized(this) {
            origins = value
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_ORIGINS, value)
                .apply()
        }
    }

    fun onSettingChanged(context: Context) {
        load(context)
        enabled = BrowserPreferences.isCrossOriginIsolationEnabled(context)
        if (!enabled) clear(context)
    }

    fun clear(context: Context) {
        load(context)
        store(context, emptySet())
        probed.clear()
    }

    private fun originOf(uri: Uri): String? {
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) 443 else uri.port
        return "https://$host:$port"
    }

    private fun supported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.CROSS_ORIGIN_ISOLATED_ALLOWLIST)

    fun applyAllowlist(webView: WebView) {
        load(webView.context)
        if (!supported()) return
        installServiceWorkerClient()
        val profile = runCatching { WebViewCompat.getProfile(webView) }.getOrNull() ?: return
        val wanted = if (enabled) origins else emptySet()
        try {
            profile.setCrossOriginIsolatedAllowlist(wanted)
        } catch (e: IllegalArgumentException) {
            val valid = wanted.filter { runCatching { profile.setCrossOriginIsolatedAllowlist(setOf(it)) }.isSuccess }.toSet()
            runCatching { profile.setCrossOriginIsolatedAllowlist(valid) }
            store(webView.context, valid)
        }
    }

    // Documents served by a service worker never reach WebViewClient.
    private fun installServiceWorkerClient() {
        if (swClientInstalled) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
        ) return
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url ?: return null
                    val target = originOf(url) ?: return null
                    val requestOrigin = header(request, "Origin")
                    if (requestOrigin != null && originOf(Uri.parse(requestOrigin)) != target) return null
                    val accept = header(request, "Accept").orEmpty()
                    val path = url.path.orEmpty()
                    val isDocument = accept.startsWith("text/html") ||
                        ((accept.isEmpty() || accept == "*/*") &&
                            (path.isEmpty() || path.endsWith("/") || path.endsWith(".html") || path.endsWith(".htm")))
                    return if (isDocument) proxy(request, sendCookies = true) else null
                }
            }
        )
        swClientInstalled = true
    }

    // Element fullscreen started by an isolated WebGPU page leaves it on a black screen.
    private val NO_FULLSCREEN_JS = """
        (function() {
          try {
            if (!self.crossOriginIsolated) return;
            Element.prototype.requestFullscreen = function() {
              return Promise.reject(new TypeError('Fullscreen is not available'));
            };
            if (Element.prototype.webkitRequestFullscreen) Element.prototype.webkitRequestFullscreen = function() {};
            Object.defineProperty(Document.prototype, 'fullscreenEnabled', { get: function() { return false; } });
          } catch (e) {}
        })();
    """.trimIndent()

    fun installNoFullscreenScript(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addDocumentStartJavaScript(webView, NO_FULLSCREEN_JS, setOf("*"))
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!request.isForMainFrame) return null
        val host = request.url?.host?.lowercase() ?: return null
        val refererHost = header(request, "Referer")?.let { Uri.parse(it).host?.lowercase() }
        return proxy(request, sendCookies = refererHost == null || sameSite(host, refererHost))
    }

    private fun sameSite(a: String, b: String): Boolean =
        a == b || a.endsWith(".$b") || b.endsWith(".$a")

    private fun header(request: WebResourceRequest, name: String): String? =
        request.requestHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun proxy(request: WebResourceRequest, sendCookies: Boolean): WebResourceResponse? {
        if (!enabled || request.method != "GET") return null
        val url = request.url ?: return null
        if (originOf(url) !in origins) return null
        return runCatching { fetch(url.toString(), request.requestHeaders, sendCookies) }.getOrNull()
    }

    private fun policyFor(coop: String?, coep: String?): String? {
        fun token(v: String?) = v?.substringBefore(';')?.trim()?.lowercase()
        if (token(coop) != "same-origin") return null
        return when (token(coep)) {
            "require-corp" -> "isolate-and-require-corp"
            "credentialless" -> "isolate-and-credentialless"
            else -> null
        }
    }

    fun checkPage(view: WebView, url: String?) {
        if (!enabled || !supported() || url == null) return
        val origin = originOf(Uri.parse(url)) ?: return
        if (origin in origins) return
        view.evaluateJavascript("self.crossOriginIsolated ? '' : location.origin") { result ->
            val pageOrigin = result?.trim('"')?.takeIf { it.isNotEmpty() && it != "null" } ?: return@evaluateJavascript
            if (originOf(Uri.parse(pageOrigin)) != origin || !probed.add(origin)) return@evaluateJavascript
            probeExecutor.execute {
                if (runCatching { probe(url) }.getOrNull() == null) return@execute
                view.post {
                    if (!enabled || originOf(Uri.parse(view.url ?: "")) != origin) return@post
                    store(view.context, origins + origin)
                    applyAllowlist(view)
                    view.reload()
                }
            }
        }
    }

    private fun probe(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.responseCode
            return policyFor(
                conn.getHeaderField("Cross-Origin-Opener-Policy"),
                conn.getHeaderField("Cross-Origin-Embedder-Policy")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun fetch(url: String, requestHeaders: Map<String, String>, sendCookies: Boolean): WebResourceResponse? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        for ((k, v) in requestHeaders) {
            if (!k.equals("Accept-Encoding", ignoreCase = true)) conn.setRequestProperty(k, v)
        }
        if (requestHeaders.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            conn.setRequestProperty("Accept-Language", LocaleList.getDefault().toLanguageTags())
        }
        val cookies = CookieManager.getInstance()
        if (sendCookies) cookies.getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }

        val code = conn.responseCode
        val headers = LinkedHashMap<String, String>()
        for ((name, values) in conn.headerFields) {
            if (name == null || values.isNullOrEmpty()) continue
            when {
                name.equals("Set-Cookie", ignoreCase = true) -> values.forEach { cookies.setCookie(url, it) }
                name.equals("Content-Length", ignoreCase = true) ||
                    name.equals("Content-Encoding", ignoreCase = true) ||
                    name.equals("Transfer-Encoding", ignoreCase = true) -> Unit
                else -> headers[name] = values.joinToString(", ")
            }
        }
        // WebResourceResponse cannot carry a redirect; WebView repeats the request itself.
        if (code !in 200..299 && code !in 400..599) {
            conn.disconnect()
            return null
        }
        policyFor(headers.valueOf("Cross-Origin-Opener-Policy"), headers.valueOf("Cross-Origin-Embedder-Policy"))
            ?.let { headers[DIP_HEADER] = it }

        val contentType = conn.contentType ?: "text/html"
        val mime = contentType.substringBefore(';').trim()
        val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
        val reason = conn.responseMessage?.takeIf { it.isNotBlank() } ?: if (code < 400) "OK" else "Error"
        val body = (if (code >= 400) conn.errorStream else conn.inputStream) ?: ByteArrayInputStream(ByteArray(0))
        return WebResourceResponse(mime, charset, code, reason, headers, body)
    }

    private fun Map<String, String>.valueOf(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
