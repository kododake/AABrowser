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

package com.kododake.aabrowser.ui.compose.screens.bookmarks

/**
 * Data model for a persisted bookmark entry with title.
 */
data class BookmarkEntry(
    val url: String,
    val title: String = ""
)

/**
 * UI representation of a bookmark item.
 */
data class BookmarkItemUi(
    val url: String,
    val title: String,
    val slotIndex: Int = -1 // -1 means not pinned to start page
)

/**
 * Callbacks for user interactions within BookmarkManagerSheet.
 */
data class BookmarkActions(
    val onSelectBookmark: (String) -> Unit = {},
    val onDeleteBookmark: (String) -> Unit = {},
    val onPinBookmark: (String, Int) -> Unit = { _, _ -> },
    val onAddCurrentPage: () -> Unit = {},
    val onReorderBookmarks: (Int, Int) -> Unit = { _, _ -> },
    val onCommitBookmarkReorder: () -> Unit = {},
    /** Called by the back/close button in header → may return to menu */
    val onClose: () -> Unit = {},
    /** Called by drag dismiss or scrim tap → always closes completely, no menu return */
    val onDismiss: () -> Unit = {},
    val onDismissFinished: () -> Unit = {}
)
