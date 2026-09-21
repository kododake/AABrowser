package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.data.BrowserPreferences
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extends Shields to fetches made by service workers, which bypass every WebViewClient.
 *
 * The client is process-global and shared by all tabs. A service worker request carries no page
 * URL, so the engine is asked leniently: only unconditional rules block, and `$third-party` /
 * `$domain=` constraints fail for blocking rules and pass for exceptions (see
 * [FilterEngine.shouldBlock]). The global Shields switch is honoured; the request's own site is
 * used as a proxy for the per-site check.
 */
object ServiceWorkerShields {
    private val installed = AtomicBoolean(false)

    fun installOnce(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) return
        val appContext = context.applicationContext
        runCatching {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    if (!AdBlocker.isLoaded || !BrowserPreferences.isShieldsEnabled(appContext)) return null
                    val headers = request.requestHeaders.orEmpty()
                    val pageUrl = headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
                        ?: headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                    return AdBlocker.interceptOrNull(
                        requestUrl = request.url,
                        pageUrl = pageUrl,
                        isMainFrame = false,
                        requestHeaders = headers,
                        lenient = true
                    )
                }
            })
        }
    }
}
