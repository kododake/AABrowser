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
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.controllers.ShieldsLoadingIndicator
import com.kododake.aabrowser.web.WebViewNavigationHelper

object TabSwitcher {

    fun switchToTab(
        tabId: Long,
        browserTabs: MutableList<BrowserTab>,
        activeTabId: Long?,
        binding: ActivityMainBinding,
        callbacks: TabCallbacks,
        displayTitleForTab: (BrowserTab) -> String,
        onActiveTabChanged: (Long) -> Unit
    ) {
        val selectedTab = browserTabs.firstOrNull { it.id == tabId } ?: return

        val prevActiveId = activeTabId
        if (prevActiveId != null && prevActiveId != selectedTab.id) {
            val prevIndex = browserTabs.indexOfFirst { it.id == prevActiveId }
            if (prevIndex >= 0) {
                val prevTab = browserTabs[prevIndex]
                prevTab.webView.onPause()
                browserTabs[prevIndex] = prevTab.copy(isActive = false)
            }
        }

        onActiveTabChanged(selectedTab.id)

        for (i in browserTabs.indices) {
            val t = browserTabs[i]
            val nextActive = (t.id == selectedTab.id)
            if (t.isActive != nextActive) {
                browserTabs[i] = t.copy(isActive = nextActive)
            }
        }

        val activeTabInstance = browserTabs.firstOrNull { it.id == selectedTab.id } ?: selectedTab

        browserTabs.forEach { tab ->
            val isVisible = (tab.id == activeTabInstance.id)
            tab.webView.visibility = if (isVisible) View.VISIBLE else View.GONE
            if (isVisible) {
                tab.webView.bringToFront()
                tab.webView.requestFocus()
                tab.webView.invalidate()
            }
        }
        activeTabInstance.webView.onResume()

        if (activeTabInstance.currentUrl.isBlank()) {
            callbacks.onShowStartPage()
        } else {
            callbacks.onHideStartPage()
            if (activeTabInstance.webView.url.isNullOrBlank()) {
                WebViewNavigationHelper.navigate(
                    activeTabInstance.webView,
                    activeTabInstance.currentUrl,
                    replaceCurrentEntry = true,
                    onWaitingForShields = { waiting -> ShieldsLoadingIndicator.show(binding, waiting) }
                )
            }
        }

        callbacks.updateNavigationButtons()
        callbacks.onTabChanged(activeTabInstance)
    }
}
