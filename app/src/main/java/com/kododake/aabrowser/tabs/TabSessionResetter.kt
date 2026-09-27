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

import android.content.Context
import android.view.View
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.web.releaseCompletely

/**
 * Resets the active tab session by completely destroying the existing WebView instance
 * and replacing it with a fresh, isolated WebView instance to prevent malicious injection,
 * cross-page script leaks, history pollution, and residual background execution.
 */
object TabSessionResetter {

    fun resetActiveTab(
        context: Context,
        binding: ActivityMainBinding,
        browserTabs: MutableList<BrowserTab>,
        activeTabId: Long?,
        callbacks: TabCallbacks,
        newUrl: String
    ): BrowserTab? {
        val targetId = activeTabId ?: return null
        val tabIndex = browserTabs.indexOfFirst { it.id == targetId }
        if (tabIndex < 0) return null

        val oldTab = browserTabs[tabIndex]
        oldTab.webView.releaseCompletely()

        val newTab = BrowserTabFactory.createTab(
            context = context,
            tabId = targetId,
            initialUrl = newUrl,
            initialTitle = "",
            activate = true,
            createBrowserCallbacks = callbacks::buildBrowserCallbacks,
            onRequestSpeechMicrophone = callbacks::requestSpeechRecognitionMicrophoneAccess,
            onSanitizeJsExternalUrl = callbacks::sanitizeJsExternalUrl,
            onOpenUriExternally = callbacks::openUriExternally,
            onShowMenuButtonTemporarily = callbacks::showMenuButtonTemporarily
        )

        binding.webViewContainer.addView(newTab.webView)
        browserTabs[tabIndex] = newTab

        newTab.webView.visibility = View.VISIBLE
        newTab.webView.bringToFront()
        newTab.webView.requestFocus()
        newTab.webView.invalidate()
        newTab.webView.onResume()

        callbacks.onTabChanged(newTab)
        return newTab
    }
}
