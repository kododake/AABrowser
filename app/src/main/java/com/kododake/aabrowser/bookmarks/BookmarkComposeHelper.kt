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

package com.kododake.aabrowser.bookmarks

import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkActions
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkManagerSheet

object BookmarkComposeHelper {
    fun setupComposeBookmarks(
        manager: BookmarkManager,
        binding: ActivityMainBinding,
        callbacks: BookmarkManager.BookmarkCallbacks
    ) {
        binding.bookmarkComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isVisible by manager.isVisibleState
                val bookmarks by manager.bookmarksState
                val canAddCurrentUrl by manager.canAddCurrentUrlState
                val currentUrl by manager.currentUrlState
                val keepScrim by manager.keepScrimState

                BookmarkManagerSheet(
                    isVisible = isVisible,
                    bookmarks = bookmarks,
                    canAddCurrentUrl = canAddCurrentUrl,
                    currentUrl = currentUrl,
                    animateEnter = true,
                    keepScrimOnClose = keepScrim,
                    onProgress = callbacks::onSheetProgress,
                    actions = BookmarkActions(
                        onSelectBookmark = { url ->
                            manager.isOpenedFromMenuState.value = false
                            manager.hideBookmarkManager()
                            callbacks.onNavigateToUrl(url)
                            binding.bookmarkComposeView.visibility = View.GONE
                            binding.menuComposeView.visibility = View.GONE
                            binding.menuOverlay.visibility = View.GONE
                            callbacks.showMenuButtonTemporarily()
                        },
                        onDeleteBookmark = { url ->
                            manager.removeBookmark(url)
                        },
                        onPinBookmark = { url, slot ->
                            if (slot >= 0) {
                                BrowserPreferences.clearStartPageSlot(binding.root.context, slot)
                                Toast.makeText(binding.root.context, R.string.start_page_slot_removed, Toast.LENGTH_SHORT).show()
                                manager.refreshBookmarks()
                                callbacks.onRefreshStartPage()
                            } else {
                                manager.showStartPageSlotPicker(url)
                            }
                        },
                        onAddCurrentPage = { manager.addBookmarkForCurrentPage() },
                        onReorderBookmarks = { from, to -> manager.reorderBookmarks(from, to) },
                        onCommitBookmarkReorder = { manager.commitBookmarkReorder() },
                        onClose = {
                            if (manager.isOpenedFromMenuState.value) {
                                manager.returnToMenu()
                            } else {
                                manager.hideBookmarkManager()
                            }
                        },
                        onDismiss = { manager.hideBookmarkManager() },
                        onDismissFinished = { manager.onBookmarkDismissFinished() }
                    ),
                    faviconProvider = { url -> manager.resolveCachedSiteIcon(url) }
                )
            }
        }
    }
}
