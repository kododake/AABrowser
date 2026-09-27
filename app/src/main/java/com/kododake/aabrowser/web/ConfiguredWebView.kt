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

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.web.adblock.AdBlocker
import com.kododake.aabrowser.web.adblock.ServiceWorkerShields
import com.kododake.aabrowser.web.adblock.ShieldsRuntime
import java.util.concurrent.atomic.AtomicBoolean

fun configureWebView(
    webView: WebView,
    callbacks: BrowserCallbacks = BrowserCallbacks(),
    useDesktopMode: Boolean = false,
    userAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_CHROME,
    enableDrmL3Enforcer: Boolean = true
) {
    with(webView) {
        setBackgroundColor(Color.TRANSPARENT)

        if (enableDrmL3Enforcer && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val handler = WebViewCompat.addDocumentStartJavaScript(this, WebScripts.DRM_L3_ENFORCER_JS, setOf("*"))
            setTag(R.id.webview_drm_l3_script_handler_tag, handler)
        }

        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = true

        WebView.setWebContentsDebuggingEnabled(false)

        val originalUserAgent = settings.userAgentString
        setTag(R.id.webview_original_user_agent_tag, originalUserAgent)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(true)

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                offscreenPreRaster = true
            }
        }

        UserAgentManager.applyBrowserIdentity(this, userAgentProfile, useDesktopMode)

        // Shields: compile filters in the background. External navigation is gated by
        // loadUrlWhenShieldsReady, so no page requests can bypass the engine.
        val appContext = context.applicationContext
        val shieldsEnabled = AtomicBoolean(BrowserPreferences.isShieldsEnabled(appContext))
        setTag(R.id.webview_shields_enabled_tag, shieldsEnabled)
        if (shieldsEnabled.get()) AdBlocker.ensureLoadedAsync(appContext)
        ShieldsRuntime.install(this, shieldsEnabled.get())
        ServiceWorkerShields.installOnce(appContext)

        CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.setAcceptThirdPartyCookies(this, true)
        }

        webViewClient = BrowserWebViewClient(callbacks, shieldsEnabled)


        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChange(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                callbacks.onTitleChange(title)
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                val pageUrl = view?.url?.takeIf { it.isNotBlank() } ?: return
                callbacks.onFaviconReceived(pageUrl, icon)
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
                    return
                }

                val allowed = setOf(
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                )

                val grantable = request.resources.filter { it in allowed }.toTypedArray()

                if (grantable.isEmpty()) {
                    request.deny()
                    return
                }

                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in grantable) {
                    callbacks.onPermissionRequest(request)
                } else {
                    this@with.post { request.grant(grantable) }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                callbacks.onGeolocationPermissionRequest(origin, callback)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false
                val newWebView = callbacks.onCreateNewWindow()
                if (newWebView != null) {
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        transport.webView = newWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                } else {
                    val hitTest = view?.hitTestResult
                    val extraUrl = hitTest?.extra
                    if (!extraUrl.isNullOrBlank()) {
                        view.loadUrl(extraUrl)
                        return true
                    }
                }
                return false
            }
        }

        setDownloadListener(DownloadListener { url, _, _, _, _ ->
            val uri = url?.takeIf { it.isNotBlank() }?.toUri() ?: return@DownloadListener
            callbacks.onShowDownloadPrompt(uri)
        })

        if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
            WebViewCompat.addNavigationListener(
                this,
                ContextCompat.getMainExecutor(context),
                object : NavigationListener {
                    override fun onNavigationStarted(navigation: Navigation) {}
                    override fun onNavigationCompleted(navigation: Navigation) {
                        val navUrl = navigation.url.takeIf { it.isNotBlank() } ?: navigation.page?.url
                        navUrl?.let(callbacks.onUrlChange)
                    }
                }
            )
        }
    }
}

fun WebView.updateDesktopMode(enable: Boolean, profile: UserAgentProfile) {
    UserAgentManager.applyBrowserIdentity(this, profile, enable)
    reload()
}

fun WebView.updateUserAgentProfile(profile: UserAgentProfile, desktop: Boolean) {
    UserAgentManager.applyBrowserIdentity(this, profile, desktop)
    reload()
}

/**
 * Loads only after Shields has its complete filter engine, without blocking the UI thread.
 * [load] performs the actual navigation once the engine is ready (plain [WebView.loadUrl] by default).
 */
fun WebView.loadUrlWhenShieldsReady(
    url: String,
    onWaitingForShields: (Boolean) -> Unit = {},
    load: (String) -> Unit = { loadUrl(it) }
) {
    if (!BrowserPreferences.isShieldsEnabled(context)) {
        load(url)
        return
    }
    val target = this
    if (!AdBlocker.isLoaded) onWaitingForShields(true)
    target.setTag(R.id.webview_pending_shields_url_tag, url)
    AdBlocker.runWhenLoaded(context.applicationContext) {
        onWaitingForShields(false)
        // Closed tabs are removed from their parent and destroyed while filters load.
        if (target.parent != null && target.getTag(R.id.webview_pending_shields_url_tag) == url) {
            target.setTag(R.id.webview_pending_shields_url_tag, null)
            load(url)
        }
    }
}

fun WebView.updateShieldsEnabled(enabled: Boolean) {
    val state = (getTag(R.id.webview_shields_enabled_tag) as? AtomicBoolean)
        ?: AtomicBoolean(enabled).also { setTag(R.id.webview_shields_enabled_tag, it) }
    state.set(enabled)
    if (!enabled) {
        ShieldsRuntime.install(this, false)
        reload()
        return
    }
    val target = this
    AdBlocker.runWhenLoaded(context.applicationContext) {
        if (state.get() && target.parent != null) {
            ShieldsRuntime.install(target, true)
            target.reload()
        }
    }
}

fun WebView.releaseCompletely() {
    stopLoading()
    ShieldsRuntime.uninstall(this)
    (parent as? android.view.ViewGroup)?.removeView(this)
    removeAllViews()
    webChromeClient = null
    webViewClient = WebViewClient()
    destroy()
}

fun WebView.updateDrmL3Enforcer(enabled: Boolean, reload: Boolean = true) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        val existingHandler = getTag(R.id.webview_drm_l3_script_handler_tag) as? ScriptHandler
        if (enabled) {
            if (existingHandler == null) {
                val handler = WebViewCompat.addDocumentStartJavaScript(this, WebScripts.DRM_L3_ENFORCER_JS, setOf("*"))
                setTag(R.id.webview_drm_l3_script_handler_tag, handler)
            }
        } else {
            existingHandler?.remove()
            setTag(R.id.webview_drm_l3_script_handler_tag, null)
        }
    }
    if (reload) {
        reload()
    }
}

