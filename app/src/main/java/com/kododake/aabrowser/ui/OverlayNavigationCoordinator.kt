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

package com.kododake.aabrowser.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.tabs.TabManager

/**
 * Unified coordinator managing navigation, transitions, and back press handling
 * across the browser menu and all full-screen overlay subscreens:
 * Bookmarks, Tabs, Settings, QR Code, and Version Check.
 */
class OverlayNavigationCoordinator(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val uiManager: BrowserUIManager,
    private val bookmarkManager: BookmarkManager,
    private val tabManager: TabManager,
    private val overlayManager: OverlayManager,
    private val onShowMenuButtonTemporarily: () -> Unit
) {
    enum class OverlayScreen {
        NONE,
        MENU,
        BOOKMARKS,
        TABS,
        SETTINGS,
        QR_CODE,
        VERSION
    }

    var currentScreen: OverlayScreen = OverlayScreen.NONE
        private set

    var isSubscreenOpenedFromMenu: Boolean = false
        private set

    fun showMenu(focusAddressBar: Boolean = false) {
        hideSubscreens()
        currentScreen = OverlayScreen.MENU
        isSubscreenOpenedFromMenu = false
        uiManager.showMenuOverlay(focusAddressBar)
    }

    fun openBookmarks(fromMenu: Boolean = true) {
        prepareSubscreen(fromMenu, OverlayScreen.BOOKMARKS)
        bookmarkManager.showBookmarkManager(fromMenu)
    }

    fun openTabs(fromMenu: Boolean = true) {
        prepareSubscreen(fromMenu, OverlayScreen.TABS)
        tabManager.showTabManager(fromMenu)
    }

    fun openSettings(fromMenu: Boolean = true) {
        prepareSubscreen(fromMenu, OverlayScreen.SETTINGS)
        overlayManager.showSettingsView(fromMenu)
    }

    fun openQrCode(url: String, fromMenu: Boolean = true) {
        prepareSubscreen(fromMenu, OverlayScreen.QR_CODE)
        overlayManager.showQrCodeView(url, fromMenu)
    }

    fun openVersion(fromMenu: Boolean = true) {
        prepareSubscreen(fromMenu, OverlayScreen.VERSION)
        overlayManager.showCheckLatestView(fromMenu)
    }

    fun onScreenProgress(screen: OverlayScreen, progress: Float) {
        val isActive = if (screen == OverlayScreen.MENU) {
            currentScreen == OverlayScreen.MENU || currentScreen == OverlayScreen.NONE
        } else {
            screen == currentScreen
        }
        if (isActive) {
            uiManager.applyFrostedGlassProgress(progress)
        }
    }

    private fun prepareSubscreen(fromMenu: Boolean, screen: OverlayScreen) {
        isSubscreenOpenedFromMenu = fromMenu
        currentScreen = screen
        binding.menuOverlay.visibility = View.VISIBLE
        if (fromMenu) {
            uiManager.menuHelper.hideMenu()
            binding.menuComposeView.visibility = View.GONE
        }
        uiManager.applyFrostedGlassProgress(1f)
    }

    fun returnToMenu() {
        hideSubscreens()
        currentScreen = OverlayScreen.MENU
        isSubscreenOpenedFromMenu = false
        uiManager.menuHelper.stateHolder.isReturningFromSubscreen = true
        uiManager.showMenuOverlay()
    }

    fun hideSubscreens() {
        bookmarkManager.hideBookmarkManager()
        tabManager.hideTabManager()
        overlayManager.hideSettingsView()
        overlayManager.hideQrCodeView()
        overlayManager.hideCheckLatestView()
        binding.bookmarkComposeView.visibility = View.GONE
        binding.tabComposeView.visibility = View.GONE
        binding.settingsComposeView.visibility = View.GONE
        binding.qrCodeComposeView.visibility = View.GONE
        binding.versionComposeView.visibility = View.GONE
    }

    fun hideAll() {
        hideSubscreens()
        currentScreen = OverlayScreen.NONE
        isSubscreenOpenedFromMenu = false
        uiManager.hideMenuOverlay()
        binding.menuOverlay.visibility = View.GONE
        uiManager.applyFrostedGlassProgress(0f)
        onShowMenuButtonTemporarily()
    }

    fun handleBackPressed(): Boolean {
        if (binding.settingsComposeView.isVisible || currentScreen == OverlayScreen.SETTINGS) {
            if (isSubscreenOpenedFromMenu || overlayManager.isSettingsOpenedFromMenu) {
                returnToMenu()
            } else {
                overlayManager.hideSettingsView()
            }
            return true
        }
        if (binding.bookmarkComposeView.isVisible || currentScreen == OverlayScreen.BOOKMARKS) {
            if (isSubscreenOpenedFromMenu || bookmarkManager.isOpenedFromMenu) {
                returnToMenu()
            } else {
                bookmarkManager.hideBookmarkManager()
            }
            return true
        }
        if (binding.tabComposeView.isVisible || currentScreen == OverlayScreen.TABS) {
            if (isSubscreenOpenedFromMenu || tabManager.isOpenedFromMenu) {
                returnToMenu()
            } else {
                tabManager.hideTabManager()
            }
            return true
        }
        if (binding.qrCodeComposeView.isVisible || currentScreen == OverlayScreen.QR_CODE) {
            if (isSubscreenOpenedFromMenu || overlayManager.isQrOpenedFromMenu) {
                returnToMenu()
            } else {
                overlayManager.hideQrCodeView()
            }
            return true
        }
        if (binding.versionComposeView.isVisible || currentScreen == OverlayScreen.VERSION) {
            if (isSubscreenOpenedFromMenu || overlayManager.isVersionOpenedFromMenu) {
                returnToMenu()
            } else {
                overlayManager.hideCheckLatestView()
            }
            return true
        }
        if (binding.menuComposeView.isVisible || binding.menuOverlay.isVisible || currentScreen == OverlayScreen.MENU) {
            uiManager.hideMenuOverlay()
            return true
        }
        return false
    }
}
