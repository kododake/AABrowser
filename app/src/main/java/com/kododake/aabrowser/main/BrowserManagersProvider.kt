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

import com.kododake.aabrowser.analytics.UmamiTracker
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.navigation.NavigationManager
import com.kododake.aabrowser.permissions.PermissionManager
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.BrowserUIManager
import com.kododake.aabrowser.ui.OverlayManager
import com.kododake.aabrowser.ui.ThemeManager

/**
 * Provider interface to resolve managers lazily on demand, breaking circular dependency cycles during initialization.
 */
interface BrowserManagersProvider {
    val umamiTracker: UmamiTracker
    val themeManager: ThemeManager
    val permissionManager: PermissionManager
    val bookmarkManager: BookmarkManager
    val startPageManager: StartPageManager
    val tabManager: TabManager
    val uiManager: BrowserUIManager
    val navigationManager: NavigationManager
    val overlayManager: OverlayManager
    val overlayCoordinator: com.kododake.aabrowser.ui.OverlayNavigationCoordinator
    val webBrowserCallbackFactory: WebBrowserCallbackFactory
}
