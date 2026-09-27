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

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.BrowserUIManager
import com.kododake.aabrowser.ui.OverlayNavigationCoordinator

object MainActivityBackPressHandler {

    fun register(
        activity: AppCompatActivity,
        overlayCoordinator: OverlayNavigationCoordinator,
        uiManager: BrowserUIManager,
        tabManager: TabManager,
        startPageManager: StartPageManager,
        getCurrentUrl: () -> String,
        onHideStartPage: () -> Unit,
        onNavigationButtonsUpdateNeeded: () -> Unit
    ) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    uiManager.isInFullscreen() -> uiManager.exitFullscreen()
                    overlayCoordinator.handleBackPressed() -> { /* Handled by coordinator */ }
                    startPageManager.isNavigating -> startPageManager.cancelNavigationLoading()
                    startPageManager.isShowingStartPage && getCurrentUrl().isNotBlank() -> onHideStartPage()
                    tabManager.activeTab?.webView?.canGoBack() == true -> tabManager.activeTab?.webView?.goBack()
                    else -> {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                }
                onNavigationButtonsUpdateNeeded()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback)
    }
}
