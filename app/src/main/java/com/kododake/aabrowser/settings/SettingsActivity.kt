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

package com.kododake.aabrowser.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.ui.compose.screens.settings.SettingsScreen

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(BrowserPreferences.createScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(BrowserPreferences.getThemeMode(this).nightMode)
        super.onCreate(savedInstanceState)

        setContent {
            SettingsScreen(
                context = this,
                includeDragHandle = false,
                isScrollable = true,
                callbacks = SettingsCallbacks(
                    onClose = { finish() },
                    onThemeChanged = { recreate() },
                    onScaleChanged = { recreate() },
                    onHomePageChanged = { recreate() },
                    onInAppControlsChanged = { recreate() }
                )
            )
        }
    }
}
