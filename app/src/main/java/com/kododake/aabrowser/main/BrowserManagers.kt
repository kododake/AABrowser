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

import androidx.appcompat.app.AppCompatActivity
import com.kododake.aabrowser.analytics.UmamiTracker
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.navigation.NavigationManager
import com.kododake.aabrowser.permissions.PermissionManager
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.BrowserUIManager
import com.kododake.aabrowser.ui.OverlayManager
import com.kododake.aabrowser.ui.ThemeManager

/**
 * Encapsulates instantiation and lifecycle of core domain managers for MainActivity.
 * Implements BrowserManagersProvider to resolve circular dependencies cleanly at runtime.
 */
class BrowserManagers(
    val activity: AppCompatActivity,
    val binding: ActivityMainBinding,
    val host: MainActivityCallbackFactory.CallbackHost,
    val isDebugBuild: Boolean,
    val onUrlChanged: (String) -> Unit,
    val onTitleChanged: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onNavigationButtonsUpdateNeeded: () -> Unit
) : BrowserManagersProvider {
    override val umamiTracker: UmamiTracker by lazy { UmamiTracker(activity.applicationContext) }
    override val themeManager: ThemeManager by lazy { ThemeManager(activity, binding) }
    override val permissionManager: PermissionManager by lazy { PermissionManager(activity) }

    val callbackFactory: MainActivityCallbackFactory by lazy {
        MainActivityCallbackFactory(
            context = activity,
            host = host,
            provider = this
        )
    }

    override val webBrowserCallbackFactory: WebBrowserCallbackFactory by lazy {
        WebBrowserCallbackFactory(
            activity = activity,
            binding = binding,
            provider = this,
            isDebugBuild = isDebugBuild,
            onUrlChanged = { url ->
                onUrlChanged(url)
                uiManager.menuHelper.updatePage(url)
            },
            onTitleChanged = { title ->
                onTitleChanged(title)
                uiManager.menuHelper.updatePage(uiManager.menuHelper.stateHolder.url, title)
            },
            onProgressChanged = { p ->
                onProgressChanged(p)
                startPageManager.onProgressChanged(p)
            },
            onNavigationButtonsUpdateNeeded = onNavigationButtonsUpdateNeeded
        )
    }

    override val bookmarkManager: BookmarkManager by lazy {
        BookmarkManager(activity, binding, callbackFactory.createBookmarkCallbacks())
    }
    override val startPageManager: StartPageManager by lazy {
        StartPageManager(activity, binding, bookmarkManager, callbackFactory.createStartPageCallbacks())
    }
    override val tabManager: TabManager by lazy {
        TabManager(activity, binding, bookmarkManager, callbackFactory.createTabCallbacks())
    }
    override val uiManager: BrowserUIManager by lazy {
        BrowserUIManager(activity, binding, tabManager, bookmarkManager, startPageManager, callbackFactory.createUICallbacks())
    }
    override val navigationManager: NavigationManager by lazy {
        NavigationManager(activity, binding, tabManager, permissionManager, startPageManager, uiManager, callbackFactory.createNavigationCallbacks())
    }
    override val overlayManager: OverlayManager by lazy {
        OverlayManager(activity, binding, tabManager, bookmarkManager, startPageManager, uiManager, callbackFactory.createOverlayCallbacks())
    }
    override val overlayCoordinator: com.kododake.aabrowser.ui.OverlayNavigationCoordinator by lazy {
        com.kododake.aabrowser.ui.OverlayNavigationCoordinator(
            activity = activity,
            binding = binding,
            uiManager = uiManager,
            bookmarkManager = bookmarkManager,
            tabManager = tabManager,
            overlayManager = overlayManager,
            onShowMenuButtonTemporarily = { host.onShowMenuButtonTemporarily() }
        )
    }
}
