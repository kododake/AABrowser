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

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import com.kododake.aabrowser.AppConstants.MENU_BUTTON_AUTO_HIDE_DELAY_MS
import com.kododake.aabrowser.AppConstants.MENU_BUTTON_SHOW_DELAY_MS
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.controllers.MenuSetupHelper

/**
 * Manages FAB auto-hide, delayed show, and visibility lifecycle.
 * Delegates actual show/hide to MenuSetupHelper (Compose state-driven).
 */
class MenuFabController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val menuHelper: MenuSetupHelper,
    private val isShowingStartPage: () -> Boolean,
    private val isInFullscreen: () -> Boolean
) {
    private val handler: Handler = Handler(Looper.getMainLooper())

    private val autoHideMenuFab = Runnable {
        if (!isShowingStartPage() && !BrowserPreferences.isQuickActionButtonAlwaysVisible(context)) {
            menuHelper.hideFab()
        }
    }

    private val showMenuFabRunnable = Runnable {
        if (!isInFullscreen() && !binding.menuOverlay.isVisible) {
            menuHelper.showFab()
            if (!isShowingStartPage() && !BrowserPreferences.isQuickActionButtonAlwaysVisible(context)) {
                handler.postDelayed(autoHideMenuFab, MENU_BUTTON_AUTO_HIDE_DELAY_MS)
            }
        }
    }

    fun showMenuButtonTemporarily() {
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        if (isInFullscreen() || binding.menuOverlay.isVisible) return
        menuHelper.showFab()
        if (!isShowingStartPage() && !BrowserPreferences.isQuickActionButtonAlwaysVisible(context)) {
            handler.postDelayed(autoHideMenuFab, 4000L)
        }
    }

    fun cleanup() {
        handler.removeCallbacks(autoHideMenuFab)
        handler.removeCallbacks(showMenuFabRunnable)
    }
}
