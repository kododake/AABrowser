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
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object HomePageSettingsSection {

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
                context.getString(R.string.settings_home_page),
                R.drawable.home_24px,
                bottomPaddingDp = 8
            )
        )

        val currentHomePage = BrowserPreferences.getHomePageUrl(context)
        val homePageStatusText = if (currentHomePage.isNullOrBlank()) {
            context.getString(R.string.settings_home_page_inactive)
        } else {
            currentHomePage
        }
        val homePageRow = builder.createSettingRow(
            title = "Home Page URL",
            statusText = homePageStatusText,
            iconRes = R.drawable.home_24px
        ) {
            val inputLayout = TextInputLayout(context).apply {
                hint = context.getString(R.string.settings_home_page_hint)
                helperText = context.getString(R.string.settings_home_page_helper)
                setPadding(builder.dp(24), builder.dp(8), builder.dp(24), builder.dp(8))
            }
            val input = TextInputEditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(currentHomePage.orEmpty())
            }
            inputLayout.addView(input)

            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Set Home Page")
                .setView(inputLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Clear") { _, _ ->
                    BrowserPreferences.clearHomePageUrl(context)
                    callbacks.onHomePageChanged()
                    Toast.makeText(context, R.string.home_page_cleared, Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val entered = input.text?.toString()?.trim().orEmpty()
                    if (entered.isNotBlank()) {
                        BrowserPreferences.setHomePageUrl(context, entered)
                        callbacks.onHomePageChanged()
                        Toast.makeText(context, R.string.home_page_set, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
        inner.addView(homePageRow)

        card.addView(inner)
        return card
    }
}
