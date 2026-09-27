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

package com.kododake.aabrowser.tabs

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.SpeechRecognitionBridge
import com.kododake.aabrowser.web.configureWebView

/**
 * Factory for instantiating and configuring WebView instances and their associated BrowserTab wrappers.
 */
object BrowserTabFactory {

    fun createTab(
        context: Context,
        tabId: Long,
        initialUrl: String?,
        initialTitle: String,
        activate: Boolean,
        createBrowserCallbacks: (BrowserTab) -> BrowserCallbacks,
        onRequestSpeechMicrophone: (tabId: Long, pageUrl: String?) -> Unit,
        onSanitizeJsExternalUrl: (sourceWebView: android.webkit.WebView, rawUrl: String?) -> Uri?,
        onOpenUriExternally: (Uri) -> Unit,
        onShowMenuButtonTemporarily: () -> Unit
    ): BrowserTab {
        val tabView = android.webkit.WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }

        lateinit var tab: BrowserTab
        val speechBridge = SpeechRecognitionBridge(tabView) { pageUrl ->
            onRequestSpeechMicrophone(tab.id, pageUrl)
        }

        tab = BrowserTab(
            id = tabId,
            webView = tabView,
            speechBridge = speechBridge,
            currentUrl = initialUrl.orEmpty(),
            currentTitle = initialTitle,
            isActive = activate
        )

        configureWebView(
            tabView,
            createBrowserCallbacks(tab),
            BrowserPreferences.shouldUseDesktopMode(context),
            BrowserPreferences.getUserAgentProfile(context),
            BrowserPreferences.isDrmL3EnforcerEnabled(context)
        )
        setupWebMessageListener(tabView, speechBridge)
        setupJavascriptInterface(context, tabView, onSanitizeJsExternalUrl, onOpenUriExternally)

        tabView.setOnTouchListener { _, _ ->
            onShowMenuButtonTemporarily()
            false
        }
        tabView.onPause()

        return tab
    }

    private fun setupWebMessageListener(webView: android.webkit.WebView, speechBridge: SpeechRecognitionBridge) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                SpeechRecognitionBridge.BRIDGE_OBJECT_NAME,
                setOf("*")
            ) { webViewInstance, message, sourceOrigin, isMainFrame, _ ->
                speechBridge.handleWebMessage(message, sourceOrigin, isMainFrame, webViewInstance.url)
            }
        }
    }

    private fun setupJavascriptInterface(
        context: Context,
        webView: android.webkit.WebView,
        onSanitizeJsExternalUrl: (android.webkit.WebView, String?) -> Uri?,
        onOpenUriExternally: (Uri) -> Unit
    ) {
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun openExternal(url: String) {
                (context as? Activity)?.runOnUiThread {
                    val safeUri = onSanitizeJsExternalUrl(webView, url)
                    if (safeUri != null) {
                        onOpenUriExternally(safeUri)
                    }
                }
            }
        }, "Android")
    }
}
