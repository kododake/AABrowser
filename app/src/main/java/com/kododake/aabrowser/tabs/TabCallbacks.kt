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

import android.net.Uri
import com.kododake.aabrowser.web.BrowserCallbacks

interface TabCallbacks {
    fun onTabChanged(tab: BrowserTab)
    fun buildBrowserCallbacks(tab: BrowserTab): BrowserCallbacks
    fun onNavigateToUrl(url: String)
    fun onShowStartPage()
    fun onHideStartPage()
    fun onShowMenuOverlay(focusAddressBar: Boolean = false)
    fun onHideMenuOverlay()
    fun resolveThemeColor(attrRes: Int): Int
    fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int
    fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?)
    fun onSpeechTabClosed(tabId: Long)
    fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): Uri?
    fun openUriExternally(uri: Uri)
    fun updateNavigationButtons()
    fun showMenuButtonTemporarily()
    fun onReturnToMenuRequested() {}
    fun onDismissOverlaysRequested() {}
    fun onSheetProgress(progress: Float) {}
}

