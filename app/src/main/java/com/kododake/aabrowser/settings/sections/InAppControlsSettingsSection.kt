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
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object InAppControlsSettingsSection {

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
                context.getString(R.string.settings_in_app_controls),
                R.drawable.search_24px,
                bottomPaddingDp = 8
            )
        )

        val currentButtonMode = BrowserPreferences.getQuickActionButtonMode(context)
        val buttonModeStatus = when (currentButtonMode) {
            QuickActionButtonMode.MENU -> context.getString(R.string.settings_quick_action_button_mode_menu)
            QuickActionButtonMode.ADDRESS_BAR -> context.getString(R.string.settings_quick_action_button_mode_address_bar)
        }
        val buttonModeRow = builder.createSettingRow(
            title = context.getString(R.string.settings_quick_action_button_mode),
            statusText = buttonModeStatus,
            iconRes = R.drawable.settings_24px
        ) {
            val modes = arrayOf(
                context.getString(R.string.settings_quick_action_button_mode_menu),
                context.getString(R.string.settings_quick_action_button_mode_address_bar)
            )
            val selectedIndex = if (currentButtonMode == QuickActionButtonMode.ADDRESS_BAR) 1 else 0
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Quick Action Mode")
                .setSingleChoiceItems(modes, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val newMode = if (which == 1) QuickActionButtonMode.ADDRESS_BAR else QuickActionButtonMode.MENU
                    if (newMode != currentButtonMode) {
                        BrowserPreferences.setQuickActionButtonMode(context, newMode)
                        callbacks.onInAppControlsChanged()
                    }
                }
                .show()
        }
        inner.addView(buttonModeRow)

        val alwaysVisibleRow = builder.createSettingSwitchRow(
            title = context.getString(R.string.settings_quick_action_button_always_visible),
            description = context.getString(R.string.settings_quick_action_button_always_visible_description),
            iconRes = R.drawable.settings_24px,
            isCheckedValue = BrowserPreferences.isQuickActionButtonAlwaysVisible(context)
        ) { isChecked ->
            BrowserPreferences.setQuickActionButtonAlwaysVisible(context, isChecked)
            callbacks.onInAppControlsChanged()
        }
        inner.addView(alwaysVisibleRow)

        val currentPosition = BrowserPreferences.getQuickActionButtonPosition(context)
        val positionStatus = when (currentPosition) {
            QuickActionButtonPosition.BOTTOM_LEFT -> context.getString(R.string.settings_quick_action_button_position_bottom_left)
            QuickActionButtonPosition.BOTTOM_RIGHT -> context.getString(R.string.settings_quick_action_button_position_bottom_right)
            QuickActionButtonPosition.TOP_LEFT -> context.getString(R.string.settings_quick_action_button_position_top_left)
            QuickActionButtonPosition.TOP_RIGHT -> context.getString(R.string.settings_quick_action_button_position_top_right)
        }
        val positionRow = builder.createSettingRow(
            title = context.getString(R.string.settings_quick_action_button_position),
            statusText = positionStatus,
            iconRes = R.drawable.settings_24px
        ) {
            val positions = arrayOf(
                context.getString(R.string.settings_quick_action_button_position_bottom_left),
                context.getString(R.string.settings_quick_action_button_position_bottom_right),
                context.getString(R.string.settings_quick_action_button_position_top_left),
                context.getString(R.string.settings_quick_action_button_position_top_right)
            )
            val selectedIndex = when (currentPosition) {
                QuickActionButtonPosition.BOTTOM_LEFT -> 0
                QuickActionButtonPosition.BOTTOM_RIGHT -> 1
                QuickActionButtonPosition.TOP_LEFT -> 2
                QuickActionButtonPosition.TOP_RIGHT -> 3
            }
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Button Position")
                .setSingleChoiceItems(positions, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val newPos = when (which) {
                        1 -> QuickActionButtonPosition.BOTTOM_RIGHT
                        2 -> QuickActionButtonPosition.TOP_LEFT
                        3 -> QuickActionButtonPosition.TOP_RIGHT
                        else -> QuickActionButtonPosition.BOTTOM_LEFT
                    }
                    if (newPos != currentPosition) {
                        BrowserPreferences.setQuickActionButtonPosition(context, newPos)
                        callbacks.onInAppControlsChanged()
                    }
                }
                .show()
        }
        inner.addView(positionRow)

        card.addView(inner)
        return card
    }
}
