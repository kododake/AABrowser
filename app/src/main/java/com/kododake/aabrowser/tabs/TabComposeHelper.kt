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

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.bookmarks.BookmarkIconUtils
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.compose.screens.tabs.TabActions
import com.kododake.aabrowser.ui.compose.screens.tabs.TabManagerSheet

object TabComposeHelper {
    fun setupComposeTabs(
        manager: TabManager,
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
        callbacks: TabCallbacks
    ) {
        binding.tabComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isVisible by manager.isVisibleState
                val tabs by manager.tabsState
                val keepScrim by manager.keepScrimState

                TabManagerSheet(
                    isVisible = isVisible,
                    tabs = tabs,
                    animateEnter = true,
                    keepScrimOnClose = keepScrim,
                    onProgress = callbacks::onSheetProgress,
                    actions = TabActions(
                        onSelectTab = { tabId -> manager.dismissTabManagerToPage { manager.switchToTab(tabId) } },
                        onCloseTab = { tabId ->
                            manager.closeTab(tabId) { callbacks.onSpeechTabClosed(tabId) }
                        },
                        onNewTab = { manager.dismissTabManagerToPage { manager.createNewTab(true) } },
                        onReorderTabs = { from, to -> manager.reorderTabs(from, to) },
                        onCommitTabReorder = { manager.commitTabReorder() },
                        onClose = {
                            if (manager.isOpenedFromMenuState.value) {
                                manager.returnToMenu()
                            } else {
                                manager.hideTabManager()
                            }
                        },
                        onDismiss = { manager.hideTabManager() },
                        onDismissFinished = { manager.onTabDismissFinished() }
                    ),
                    faviconProvider = { url ->
                        BookmarkIconUtils.resolveCachedSiteIcon(activity, url) { manager.refreshTabs() }
                    }
                )
            }
        }
    }
}
