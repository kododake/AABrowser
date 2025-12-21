package com.kododake.aabrowser.web

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.kododake.aabrowser.R

data class BrowserCallbacks(
    val onUrlChange: (String) -> Unit = {},
    val onTitleChange: (String?) -> Unit = {},
    val onProgressChange: (Int) -> Unit = {},
    val onShowDownloadPrompt: (Uri) -> Unit = {},
    val onError: (Int, String?) -> Unit = { _, _ -> },
    val onCleartextNavigationRequested: (
        Uri,
        allowOnce: () -> Unit,
        allowHostPermanently: () -> Unit,
        cancel: () -> Unit
    ) -> Unit = { _, _, _, cancel -> cancel() },
    val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> },
    val onExitFullscreen: () -> Unit = {}
)

fun configureWebView(
    webView: WebView,
    callbacks: BrowserCallbacks = BrowserCallbacks(),
    useDesktopMode: Boolean = false
) {
    with(webView) {
    setBackgroundColor(Color.BLACK)
    isHorizontalScrollBarEnabled = false
    isVerticalScrollBarEnabled = true

    WebView.setWebContentsDebuggingEnabled(false)

        val originalUserAgent = settings.userAgentString
        setTag(R.id.webview_original_user_agent_tag, originalUserAgent)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = buildUserAgent(originalUserAgent, useDesktopMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                offscreenPreRaster = true
            }
        }
        val scale = context.resources.displayMetrics.density * 100
        setInitialScale(scale.toInt())

        CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.setAcceptThirdPartyCookies(this, true)
        }

        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http") {
                    val host = uri.host?.lowercase()
                    if (!com.kododake.aabrowser.data.BrowserPreferences.isHostAllowedCleartext(view.context, host)) {
                        val allowOnce = {
                            view.post { view.loadUrl(uri.toString()) }
                            kotlin.Unit
                        }
                        val allowHost = {
                            view.context?.let { ctx ->
                                val hostToStore = uri.host?.lowercase()
                                if (hostToStore != null) com.kododake.aabrowser.data.BrowserPreferences.addAllowedCleartextHost(ctx, hostToStore)
                            }
                            view.post { view.loadUrl(uri.toString()) }
                            kotlin.Unit
                        }
                        val cancel = { kotlin.Unit }
                        callbacks.onCleartextNavigationRequested(uri, allowOnce, allowHost, cancel)
                        return true
                    }
                }
                return handleUri(view, uri)
            }

            private fun handleUri(view: WebView, uri: Uri?): Boolean {
                uri ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == null || scheme in setOf("http", "https", "about", "file", "data", "javascript")) {
                    return false
                }

                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val stringUrl = url ?: return
                val uri = Uri.parse(stringUrl)
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http") {
                    val host = uri.host?.lowercase()
                    val allowedOnce = getTag(R.id.webview_allow_once_uri_tag) as? String
                    if (allowedOnce == stringUrl) {
                        setTag(R.id.webview_allow_once_uri_tag, null)
                    } else if (!com.kododake.aabrowser.data.BrowserPreferences.isHostAllowedCleartext(view.context, host)) {
                        stopLoading()
                        val allowOnce = {
                            setTag(R.id.webview_allow_once_uri_tag, stringUrl)
                            view.post { view.loadUrl(stringUrl) }
                            kotlin.Unit
                        }
                        val allowHost = {
                            view.context?.let { ctx ->
                                val hostToStore = uri.host?.lowercase()
                                if (hostToStore != null) com.kododake.aabrowser.data.BrowserPreferences.addAllowedCleartextHost(ctx, hostToStore)
                            }
                            view.post { view.loadUrl(stringUrl) }
                            kotlin.Unit
                        }
                        val cancel = { kotlin.Unit }
                        callbacks.onCleartextNavigationRequested(uri, allowOnce, allowHost, cancel)
                        return
                    }
                }
                url.let(callbacks.onUrlChange)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                url?.let(callbacks.onUrlChange)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val code = error.errorCode
                    val shouldShowErrorPage = when (code) {
                        WebViewClient.ERROR_HOST_LOOKUP,
                        WebViewClient.ERROR_CONNECT,
                        WebViewClient.ERROR_TIMEOUT,
                        WebViewClient.ERROR_UNKNOWN,
                        WebViewClient.ERROR_PROXY_AUTHENTICATION -> true
                        else -> false
                    }

                    if (shouldShowErrorPage) {
                        val failed = request.url?.toString().orEmpty()
                        val message = error.description?.toString().orEmpty()
                        val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(failed)}&code=$code&message=${Uri.encode(message)}"
                        try {
                            view.loadUrl(assetUrl)
                        } catch (_: Exception) {
                            callbacks.onError(code, error.description?.toString())
                        }
                        return
                    }
                }

                callbacks.onError(error.errorCode, error.description?.toString())
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    val status = try { errorResponse.statusCode } catch (_: Exception) { -1 }
                    val reason = errorResponse.reasonPhrase ?: ""
                    val failed = request.url?.toString().orEmpty()
                    val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(failed)}&httpStatus=$status&message=${Uri.encode(reason)}"
                    try {
                        view.loadUrl(assetUrl)
                        return
                    } catch (_: Exception) {

                    }
                    callbacks.onError(status, reason)
                    return
                }

            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val primary = try { error.primaryError } catch (_: Exception) { -1 }
                val url = error.url ?: ""
                val message = "SSL error: $primary"
                val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(url)}&sslError=$primary&message=${Uri.encode(message)}"
                try {
                    view.loadUrl(assetUrl)
                    handler.cancel()
                    return
                } catch (_: Exception) {

                }
                handler.cancel()
                callbacks.onError(primary, message)
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                val shouldShowErrorPage = when (errorCode) {
                    WebViewClient.ERROR_HOST_LOOKUP,
                    WebViewClient.ERROR_CONNECT,
                    WebViewClient.ERROR_TIMEOUT,
                    WebViewClient.ERROR_UNKNOWN,
                    WebViewClient.ERROR_PROXY_AUTHENTICATION -> true
                    else -> false
                }

                if (shouldShowErrorPage) {
                    val failed = failingUrl.orEmpty()
                    val message = description.orEmpty()
                    val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(failed)}&code=$errorCode&message=${Uri.encode(message)}"
                    try {
                        view.loadUrl(assetUrl)
                        return
                    } catch (_: Exception) {
                        
                    }
                }

                callbacks.onError(errorCode, description)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChange(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                callbacks.onTitleChange(title)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    callbacks.onEnterFullscreen(view, callback)
                } else {
                    super.onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                callbacks.onExitFullscreen()
                super.onHideCustomView()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) {
                    super.onPermissionRequest(null)
                    return
                }

                val grantable = request.resources.filter { resource ->
                    resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                    }
                    .toTypedArray()

                if (grantable.isEmpty()) {
                    request.deny()
                    return
                }

                this@with.post { request.grant(grantable) }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return false
            }
        }

        setDownloadListener(DownloadListener { url, _, _, _, _ ->
            val uri = url?.takeIf { it.isNotBlank() }?.toUri() ?: return@DownloadListener
            callbacks.onShowDownloadPrompt(uri)
        })
    }
}

fun WebView.updateDesktopMode(enable: Boolean) {
    val originalUa = getTag(R.id.webview_original_user_agent_tag) as? String ?: settings.userAgentString
    settings.userAgentString = buildUserAgent(originalUa, enable)
    settings.useWideViewPort = enable
    settings.loadWithOverviewMode = enable
    reload()
}

fun WebView.releaseCompletely() {
    stopLoading()
    webChromeClient = WebChromeClient()
    webViewClient = WebViewClient()
    destroy()
}

private const val CUSTOM_USER_AGENT_SUFFIX = "AndroidAutoBrowser/1.0"
private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

private fun buildUserAgent(base: String, desktop: Boolean): String {
    val resolved = if (desktop) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
    return if (resolved.contains(CUSTOM_USER_AGENT_SUFFIX)) {
        resolved
    } else {
        "$resolved $CUSTOM_USER_AGENT_SUFFIX"
    }
}
