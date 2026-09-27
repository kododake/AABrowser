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

package com.kododake.aabrowser.main

import android.net.Uri
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.navigation.NavigationManager
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.BrowserTab
import com.kododake.aabrowser.tabs.TabCallbacks
import com.kododake.aabrowser.ui.BrowserUIManager
import com.kododake.aabrowser.ui.OverlayManager
import com.kododake.aabrowser.ui.OverlayNavigationCoordinator
import com.kododake.aabrowser.web.BrowserCallbacks

class MainActivityCallbackFactory(
    private val context: android.content.Context,
    private val host: CallbackHost,
    private val provider: BrowserManagersProvider
) {

    interface CallbackHost {
        val currentUrl: String
        val currentPageTitle: String
        fun onUrlChanged(url: String)
        fun onTitleChanged(title: String)
        fun onTabChanged(tab: BrowserTab)
        fun onShowStartPage()
        fun onHideStartPage()
        fun onUpdateNavigationButtons()
        fun onShowMenuButtonTemporarily()
        fun onHomePagePreferenceChanged()
        fun onRebuildSettingsContent()
        fun onQuickActionButtonPressed()
        fun onPickBackgroundRequested()
        fun onVersionInfoReceived(latestUrl: String, tagName: String)
        fun onRecreateRequested()
    }

    fun createBookmarkCallbacks(): BookmarkManager.BookmarkCallbacks = object : BookmarkManager.BookmarkCallbacks {
        override fun onNavigateToUrl(url: String) {
            provider.navigationManager.navigateFromBookmark(url)
            provider.uiManager.hideMenuOverlay()
        }
        override fun onRefreshStartPage() = provider.startPageManager.refreshStartPage()
        override fun onRebuildSettings() = host.onRebuildSettingsContent()
        override fun getCurrentUrl(): String = host.currentUrl
        override fun getCurrentPageTitle(): String = host.currentPageTitle
        override fun isShowingStartPage(): Boolean = provider.startPageManager.isShowingStartPage
        override fun resolveThemeColor(attrRes: Int): Int = provider.themeManager.resolveThemeColor(attrRes)
        override fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int =
            provider.themeManager.resolveReadableTextColor(backgroundColor, preferredColor, fallbackColor)
        override fun isHomePageEnabled(): Boolean = !BrowserPreferences.getHomePageUrl(context).isNullOrBlank()
        override fun handleHomePagePreferenceChanged() = host.onHomePagePreferenceChanged()
        override fun onReturnToMenuRequested() {
            provider.overlayCoordinator.returnToMenu()
        }
        override fun onDismissOverlaysRequested() {
            provider.overlayCoordinator.hideAll()
        }
        override fun showMenuButtonTemporarily() = host.onShowMenuButtonTemporarily()
        override fun onSheetProgress(progress: Float) {
            provider.overlayCoordinator.onScreenProgress(OverlayNavigationCoordinator.OverlayScreen.BOOKMARKS, progress)
        }
    }

    fun createStartPageCallbacks(): StartPageManager.StartPageCallbacks = object : StartPageManager.StartPageCallbacks {
        override fun onNavigateToUrl(url: String) {
            provider.navigationManager.loadUrlFromIntent(url)
            provider.uiManager.hideMenuOverlay()
        }
        override fun onShowMenuOverlay() = provider.uiManager.showMenuOverlay()
        override fun onHideMenuOverlay() = provider.uiManager.hideMenuOverlay()
        override fun onEnterFullscreen() {}
        override fun onExitFullscreen() = provider.uiManager.exitFullscreen()
        override fun isInFullscreen(): Boolean = provider.uiManager.isInFullscreen()
        override fun getCurrentUrl(): String = host.currentUrl
        override fun resolveThemeColor(attrRes: Int): Int = provider.themeManager.resolveThemeColor(attrRes)
        override fun updateNavigationButtons() = host.onUpdateNavigationButtons()
        override fun showMenuButtonTemporarily() = host.onShowMenuButtonTemporarily()
        override fun loadUrlFromIntent(url: String) = provider.navigationManager.loadUrlFromIntent(url)
        override fun resolveReadableTextColor(bg: Int, pr: Int, fb: Int): Int =
            provider.themeManager.resolveReadableTextColor(bg, pr, fb)
    }

    fun createTabCallbacks(): TabCallbacks = object : TabCallbacks {
        override fun onTabChanged(tab: BrowserTab) = host.onTabChanged(tab)
        override fun buildBrowserCallbacks(tab: BrowserTab): BrowserCallbacks = provider.webBrowserCallbackFactory.build(tab)
        override fun onNavigateToUrl(url: String) = provider.navigationManager.loadUrlFromIntent(url)
        override fun onShowStartPage() = host.onShowStartPage()
        override fun onHideStartPage() = host.onHideStartPage()
        override fun onShowMenuOverlay(focusAddressBar: Boolean) = provider.uiManager.showMenuOverlay(focusAddressBar)
        override fun onHideMenuOverlay() = provider.uiManager.hideMenuOverlay()
        override fun resolveThemeColor(attrRes: Int): Int = provider.themeManager.resolveThemeColor(attrRes)
        override fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int =
            provider.themeManager.resolveReadableTextColor(backgroundColor, preferredColor, fallbackColor)
        override fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?) {
            provider.permissionManager.requestSpeechRecognitionMicrophoneAccess(tabId, pageUrl) { granted ->
                val tab = provider.tabManager.browserTabs.firstOrNull { it.id == tabId }
                tab?.speechBridge?.onPermissionResult(granted)
            }
        }
        override fun onSpeechTabClosed(tabId: Long) {
            if (provider.permissionManager.pendingSpeechBridgeTabId == tabId) {
                provider.permissionManager.pendingSpeechBridgeTabId = null
            }
        }
        override fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): Uri? =
            provider.uiManager.sanitizeJsExternalUrl(sourceWebView, rawUrl)
        override fun openUriExternally(uri: Uri) = provider.uiManager.openUriExternally(uri)
        override fun updateNavigationButtons() = host.onUpdateNavigationButtons()
        override fun showMenuButtonTemporarily() = host.onShowMenuButtonTemporarily()
        override fun onReturnToMenuRequested() {
            provider.overlayCoordinator.returnToMenu()
        }
        override fun onDismissOverlaysRequested() {
            provider.overlayCoordinator.hideAll()
        }
        override fun onSheetProgress(progress: Float) {
            provider.overlayCoordinator.onScreenProgress(OverlayNavigationCoordinator.OverlayScreen.TABS, progress)
        }
    }

    fun createUICallbacks(): BrowserUIManager.UICallbacks = object : BrowserUIManager.UICallbacks {
        override fun onNavigateToAddress(raw: String, closeMenuAfterNavigate: Boolean) =
            provider.navigationManager.navigateToAddress(raw, closeMenuAfterNavigate)
        override fun onShowQrCodeView() = provider.overlayCoordinator.openQrCode(host.currentUrl, fromMenu = false)
        override fun onShowCheckLatestView() = provider.overlayCoordinator.openVersion(fromMenu = false)
        override fun onShowSettingsView() = provider.overlayCoordinator.openSettings(fromMenu = false)
        override fun handleQuickActionButtonPressed() = host.onQuickActionButtonPressed()
        override fun resolveThemeColor(attrRes: Int): Int = provider.themeManager.resolveThemeColor(attrRes)
        override fun showMenuButtonTemporarily() = host.onShowMenuButtonTemporarily()
    }

    fun createNavigationCallbacks(): NavigationManager.NavigationCallbacks = object : NavigationManager.NavigationCallbacks {
        override fun onNavigationStarted(url: String) = host.onUrlChanged(url)
        override fun onNavigationFinished(url: String) = host.onUrlChanged(url)
        override fun onHideStartPage() = host.onHideStartPage()
        override fun getCurrentUrl(): String = host.currentUrl
        override fun setCurrentUrl(url: String) = host.onUrlChanged(url)
        override fun setCurrentPageTitle(title: String) = host.onTitleChanged(title)
    }

    fun createOverlayCallbacks(): OverlayManager.OverlayCallbacks = object : OverlayManager.OverlayCallbacks {
        override fun onRecreateRequested() = host.onRecreateRequested()
        override fun onHomePageChanged() = host.onHomePagePreferenceChanged()
        override fun onPickBackgroundRequested() = host.onPickBackgroundRequested()
        override fun onVersionInfoReceived(latestUrl: String, tagName: String) = host.onVersionInfoReceived(latestUrl, tagName)
        override fun onReturnToMenuRequested() {
            provider.overlayCoordinator.returnToMenu()
        }
        override fun onDismissOverlaysRequested() {
            provider.overlayCoordinator.hideAll()
        }
        override fun onScreenProgress(screen: OverlayNavigationCoordinator.OverlayScreen, progress: Float) {
            provider.overlayCoordinator.onScreenProgress(screen, progress)
        }
    }
}
