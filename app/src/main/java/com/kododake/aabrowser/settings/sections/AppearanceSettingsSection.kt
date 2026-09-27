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

package com.kododake.aabrowser.settings.sections

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object AppearanceSettingsSection {

    fun build(
        context: Context,
        builder: SettingsComponentBuilder,
        callbacks: SettingsCallbacks
    ): View {
        val card = builder.createStyledCard()
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(builder.dp(8), builder.dp(16), builder.dp(8), builder.dp(16))
        }
        inner.addView(
            builder.createSectionTitle(
                context.getString(R.string.settings_appearance),
                R.drawable.settings_24px,
                bottomPaddingDp = 8
            )
        )

        val themeMode = BrowserPreferences.getThemeMode(context)
        val themeStatusText = when (themeMode) {
            AppThemeMode.AUTO -> context.getString(R.string.settings_theme_auto)
            AppThemeMode.LIGHT -> context.getString(R.string.settings_theme_light)
            AppThemeMode.DARK -> context.getString(R.string.settings_theme_dark)
        }
        val themeRow = builder.createSettingRow(
            title = "App Theme",
            statusText = themeStatusText,
            iconRes = R.drawable.settings_24px
        ) {
            val themes = arrayOf(
                context.getString(R.string.settings_theme_auto),
                context.getString(R.string.settings_theme_light),
                context.getString(R.string.settings_theme_dark)
            )
            val selectedIndex = when (themeMode) {
                AppThemeMode.AUTO -> 0
                AppThemeMode.LIGHT -> 1
                AppThemeMode.DARK -> 2
            }
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val newMode = when (which) {
                        1 -> AppThemeMode.LIGHT
                        2 -> AppThemeMode.DARK
                        else -> AppThemeMode.AUTO
                    }
                    if (newMode != themeMode) {
                        BrowserPreferences.setThemeMode(context, newMode)
                        callbacks.onThemeChanged()
                    }
                }
                .show()
        }
        inner.addView(themeRow)

        card.addView(inner)
        return card
    }
}
