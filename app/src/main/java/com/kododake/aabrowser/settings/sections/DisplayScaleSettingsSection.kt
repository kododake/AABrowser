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
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object DisplayScaleSettingsSection {

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
                context.getString(R.string.settings_display_scale),
                R.drawable.computer_24,
                bottomPaddingDp = 8
            )
        )

        val currentScale = BrowserPreferences.getGlobalScalePercent(context)
        val scaleRow = builder.createSettingRow(
            title = context.getString(R.string.settings_display_scale),
            statusText = context.getString(R.string.settings_scale_option, currentScale),
            iconRes = R.drawable.computer_24
        ) {
            val presetOptions = listOf(85, 100, 115, 130, 150)
            val customText = "Custom..."
            val dialogOptions = presetOptions.map { context.getString(R.string.settings_scale_option, it) }.toMutableList()
            dialogOptions.add(customText)

            val selectedIndex = presetOptions.indexOf(currentScale).let { if (it >= 0) it else presetOptions.size }

            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Page Zoom")
                .setSingleChoiceItems(dialogOptions.toTypedArray(), selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    if (which == presetOptions.size) {
                        val inputLayout = TextInputLayout(context).apply {
                            hint = context.getString(R.string.settings_scale_custom_hint)
                            helperText = context.getString(
                                R.string.settings_scale_custom_helper,
                                BrowserPreferences.MIN_GLOBAL_SCALE_PERCENT,
                                BrowserPreferences.MAX_GLOBAL_SCALE_PERCENT
                            )
                            setPadding(builder.dp(24), builder.dp(8), builder.dp(24), builder.dp(8))
                        }
                        val input = TextInputEditText(context).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            setText(currentScale.toString())
                        }
                        inputLayout.addView(input)

                        MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                            .setTitle("Enter Custom Zoom")
                            .setView(inputLayout)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val entered = input.text?.toString()?.trim().orEmpty()
                                val value = entered.toIntOrNull()
                                if (value != null) {
                                    val sanitized = BrowserPreferences.sanitizeGlobalScalePercent(value)
                                    if (sanitized != currentScale) {
                                        BrowserPreferences.setGlobalScalePercent(context, sanitized)
                                        callbacks.onScaleChanged()
                                    }
                                }
                            }
                            .show()
                    } else {
                        val selectedPreset = presetOptions[which]
                        if (selectedPreset != currentScale) {
                            BrowserPreferences.setGlobalScalePercent(context, selectedPreset)
                            callbacks.onScaleChanged()
                        }
                    }
                }
                .show()
        }
        inner.addView(scaleRow)

        card.addView(inner)
        return card
    }
}
