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

package com.kododake.aabrowser.ui.compose.screens.menu

/**
 * Action callbacks for the Compose browser menu sheet.
 */
data class MenuActions(
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onDesktopToggle: (Boolean) -> Unit = {},
    val onFullscreenToggle: (Boolean) -> Unit = {},
    /** Per-site Shields switch: `true` means Shields should be ON for the current site. */
    val onShieldsSiteToggle: (Boolean) -> Unit = {},
    val onNewTab: () -> Unit = {},
    val onTabs: () -> Unit = {},
    val onBookmarks: () -> Unit = {},
    val onQrCode: () -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onNavigate: (String) -> Unit = {},
    val onClose: () -> Unit = {},
    val onGitHub: () -> Unit = {},
    val onDragDelta: (Float) -> Unit = {},
    val onDragEnd: (Float) -> Unit = {},
    val onProgress: (Float) -> Unit = {},
    val onDismissFinished: () -> Unit = {}
)
