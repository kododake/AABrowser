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

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkIconUtils
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.ui.compose.screens.tabs.TabItemUi
import com.kododake.aabrowser.web.releaseCompletely
import com.kododake.aabrowser.web.updateDesktopMode
import com.kododake.aabrowser.web.updateUserAgentProfile

class TabManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val bookmarkManager: BookmarkManager,
    private val callbacks: TabCallbacks
) {

    val browserTabs = mutableListOf<BrowserTab>()
    var activeTabId: Long? = null
    private var nextTabId: Long = 1L

    internal val isOpenedFromMenuState = mutableStateOf(false)
    val isOpenedFromMenu: Boolean
        get() = isOpenedFromMenuState.value

    internal val isVisibleState = mutableStateOf(false)
    internal val keepScrimState = mutableStateOf(false)
    internal val tabsState = mutableStateOf<List<TabItemUi>>(emptyList())

    val activeTab: BrowserTab?
        get() = browserTabs.firstOrNull { it.id == activeTabId }

    init {
        setupComposeTabs()
    }

    private fun setupComposeTabs() {
        TabComposeHelper.setupComposeTabs(this, activity, binding, callbacks)
    }

    fun initializeTabs(
        intentUrl: String?,
        homePageUrl: String?,
        lastVisitedUrl: String?,
        restoreTabsOnLaunch: Boolean,
        resumeLastPageOnLaunch: Boolean,
        shouldForceSessionRestore: Boolean
    ) {
        TabInitializer.initialize(
            context = activity,
            intentUrl = intentUrl,
            homePageUrl = homePageUrl,
            lastVisitedUrl = lastVisitedUrl,
            restoreTabsOnLaunch = restoreTabsOnLaunch,
            resumeLastPageOnLaunch = resumeLastPageOnLaunch,
            shouldForceSessionRestore = shouldForceSessionRestore,
            createTab = { url, title, activate ->
                createBrowserTab(url, title, activate)
            },
            switchToTab = ::switchToTab,
            getTabIdAtIndex = { idx -> browserTabs.getOrNull(idx)?.id },
            getTabsCount = { browserTabs.size }
        )
    }

    fun createBrowserTab(initialUrl: String?, initialTitle: String = "", activate: Boolean): BrowserTab? {
        if (browserTabs.size >= BrowserPreferences.MAX_OPEN_TABS) {
            val message = activity.getString(R.string.tab_manager_max_tabs, BrowserPreferences.MAX_OPEN_TABS)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            refreshTabs()
            return null
        }

        val tab = BrowserTabFactory.createTab(
            context = activity,
            tabId = nextTabId++,
            initialUrl = initialUrl,
            initialTitle = initialTitle,
            activate = activate,
            createBrowserCallbacks = callbacks::buildBrowserCallbacks,
            onRequestSpeechMicrophone = callbacks::requestSpeechRecognitionMicrophoneAccess,
            onSanitizeJsExternalUrl = callbacks::sanitizeJsExternalUrl,
            onOpenUriExternally = callbacks::openUriExternally,
            onShowMenuButtonTemporarily = callbacks::showMenuButtonTemporarily
        )

        binding.webViewContainer.addView(tab.webView)
        browserTabs.add(tab)

        if (activate) {
            switchToTab(tab.id)
        } else {
            persistTabSession()
            refreshTabs()
        }
        return tab
    }

    fun createNewTab(activate: Boolean): BrowserTab? {
        val initialUrl = BrowserPreferences.getHomePageUrl(activity)
        return createBrowserTab(initialUrl, if (initialUrl.isNullOrBlank()) activity.getString(R.string.tab_manager_blank_title) else "", activate)
    }

    fun switchToTab(tabId: Long) {
        TabSwitcher.switchToTab(
            tabId = tabId,
            browserTabs = browserTabs,
            activeTabId = activeTabId,
            binding = binding,
            callbacks = callbacks,
            displayTitleForTab = ::displayTitleForTab,
            onActiveTabChanged = { activeTabId = it }
        )
        persistTabSession()
        refreshTabs()
    }

    fun closeTab(tabId: Long, onSpeechTabClosed: () -> Unit) {
        val index = browserTabs.indexOfFirst { it.id == tabId }
        if (index < 0) return

        val removedTab = browserTabs.removeAt(index)
        onSpeechTabClosed()

        removedTab.speechBridge.destroy()
        binding.webViewContainer.removeView(removedTab.webView)
        removedTab.webView.releaseCompletely()

        if (browserTabs.isEmpty()) {
            createNewTab(activate = true)
            return
        }

        if (activeTabId == removedTab.id) {
            val nextIndex = index.coerceAtMost(browserTabs.lastIndex)
            switchToTab(browserTabs[nextIndex].id)
        } else {
            persistTabSession()
            refreshTabs()
        }
    }

    fun persistTabSession() {
        TabStateStore.persistTabSession(activity, browserTabs, activeTabId)
    }

    fun refreshTabs() {
        tabsState.value = browserTabs.map { tab ->
            TabItemUi(
                id = tab.id,
                title = displayTitleForTab(tab),
                url = tab.currentUrl,
                isActive = (tab.id == activeTabId)
            )
        }
    }

    fun displayTitleForTab(tab: BrowserTab): String {
        return when {
            tab.currentTitle.isNotBlank() -> tab.currentTitle
            tab.currentUrl.isNotBlank() -> bookmarkManager.displayTitleForUrl(tab.currentUrl)
            else -> activity.getString(R.string.tab_manager_blank_title)
        }
    }

    fun showTabManager(fromMenu: Boolean = false) {
        isOpenedFromMenuState.value = fromMenu
        binding.menuComposeView.visibility = View.GONE
        binding.bookmarkComposeView.visibility = View.GONE
        binding.qrCodeComposeView.visibility = View.GONE
        binding.versionComposeView.visibility = View.GONE
        binding.settingsComposeView.visibility = View.GONE
        binding.menuOverlay.visibility = View.VISIBLE
        binding.tabComposeView.visibility = View.VISIBLE
        isVisibleState.value = true
        refreshTabs()
    }

    fun returnToMenu() {
        keepScrimState.value = true
        isVisibleState.value = false
        binding.tabComposeView.visibility = View.GONE
        callbacks.onReturnToMenuRequested()
    }

    fun reorderTabs(fromIndex: Int, toIndex: Int) {
        if (fromIndex in browserTabs.indices && toIndex in browserTabs.indices && fromIndex != toIndex) {
            val moved = browserTabs.removeAt(fromIndex)
            browserTabs.add(toIndex, moved)
            refreshTabs()
        }
    }

    fun commitTabReorder() {
        persistTabSession()
    }

    fun hideTabManager() {
        isOpenedFromMenuState.value = false
        isVisibleState.value = false
    }

    internal fun onTabDismissFinished() {
        val returningToMenu = keepScrimState.value
        keepScrimState.value = false
        if (!isVisibleState.value) {
            binding.tabComposeView.visibility = View.GONE
            if (!returningToMenu) {
                callbacks.onDismissOverlaysRequested()
            }
        }
    }

    internal inline fun dismissTabManagerToPage(action: () -> Unit) {
        isOpenedFromMenuState.value = false
        hideTabManager()
        action()
        callbacks.onHideMenuOverlay()
        binding.tabComposeView.visibility = View.GONE
        binding.menuComposeView.visibility = View.GONE
        binding.menuOverlay.visibility = View.GONE
        callbacks.showMenuButtonTemporarily()
    }

    fun updateTabUrl(tabId: Long, url: String) {
        val index = browserTabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            browserTabs[index] = browserTabs[index].copy(currentUrl = url)
            persistTabSession()
        }
    }

    fun updateTabTitle(tabId: Long, title: String) {
        val index = browserTabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            browserTabs[index] = browserTabs[index].copy(currentTitle = title)
            persistTabSession()
        }
    }

    fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        val index = browserTabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            browserTabs[index] = browserTabs[index].copy(currentUrl = url, currentTitle = title)
            persistTabSession()
        }
    }

    fun resetActiveTabSession(newUrl: String): BrowserTab? =
        TabSessionResetter.resetActiveTab(activity, binding, browserTabs, activeTabId, callbacks, newUrl)

    fun destroy() {
        browserTabs.forEach { tab ->
            tab.speechBridge.destroy()
            tab.webView.releaseCompletely()
        }
        binding.webViewContainer.removeAllViews()
        browserTabs.clear()
    }

    fun updateDesktopMode(desktop: Boolean, profile: UserAgentProfile) {
        browserTabs.forEach { tab -> tab.webView.updateDesktopMode(desktop, profile) }
    }

    fun updateUserAgentProfile(profile: UserAgentProfile, desktop: Boolean) {
        browserTabs.forEach { tab -> tab.webView.updateUserAgentProfile(profile, desktop) }
    }
}
