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

package com.kododake.aabrowser.ui.compose.screens.tabs

data class TabItemUi(
    val id: Long,
    val title: String,
    val url: String,
    val isActive: Boolean
)

data class TabActions(
    val onSelectTab: (Long) -> Unit = {},
    val onCloseTab: (Long) -> Unit = {},
    val onNewTab: () -> Unit = {},
    val onReorderTabs: (Int, Int) -> Unit = { _, _ -> },
    val onCommitTabReorder: () -> Unit = {},
    /** Called by the back/close button in header → may return to menu */
    val onClose: () -> Unit = {},
    /** Called by drag dismiss or scrim tap → always closes completely, no menu return */
    val onDismiss: () -> Unit = {},
    val onDismissFinished: () -> Unit = {}
)
