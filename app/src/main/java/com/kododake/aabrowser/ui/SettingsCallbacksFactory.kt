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

import androidx.appcompat.app.AppCompatActivity
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.web.updateDrmL3Enforcer

object SettingsCallbacksFactory {

    fun create(
        activity: AppCompatActivity,
        tabManager: TabManager,
        startPageManager: StartPageManager,
        uiManager: BrowserUIManager,
        callbacks: OverlayManager.OverlayCallbacks,
        onClose: () -> Unit,
        onDismiss: () -> Unit
    ): SettingsCallbacks {
        return SettingsCallbacks(
            onClose = onClose,
            onDismiss = onDismiss,
            onThemeChanged = { callbacks.onRecreateRequested() },
            onDrmL3EnforcerChanged = {
                val enabled = BrowserPreferences.isDrmL3EnforcerEnabled(activity)
                tabManager.browserTabs.forEach { tab ->
                    tab.webView.updateDrmL3Enforcer(enabled)
                }
            },
            onUserAgentChanged = {
                val profile = BrowserPreferences.getUserAgentProfile(activity)
                val desktop = BrowserPreferences.shouldUseDesktopMode(activity)
                tabManager.updateUserAgentProfile(profile, desktop)
            },
            onScaleChanged = { callbacks.onRecreateRequested() },
            onHomePageChanged = { callbacks.onHomePageChanged() },
            onInAppControlsChanged = {
                uiManager.applyQuickActionButtonPreferences()
            },
            onPickStartPageBackground = { callbacks.onPickBackgroundRequested() },
            onClearStartPageBackground = { startPageManager.clearStartPageBackground() },
            onSponsorsVisibilityChanged = { startPageManager.refreshStartPage() }
        )
    }
}
