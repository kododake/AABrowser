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
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object UserAgentSettingsSection {

    fun build(
        context: Context,
        builder: SettingsComponentBuilder
    ): View {
        val card = builder.createStyledCard()
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(builder.dp(8), builder.dp(16), builder.dp(8), builder.dp(16))
        }
        inner.addView(
            builder.createSectionTitle(
                context.getString(R.string.settings_user_agent),
                R.drawable.devices_other_24px,
                bottomPaddingDp = 8
            )
        )

        val currentProfile = BrowserPreferences.getUserAgentProfile(context)
        val uaStatusText = if (currentProfile == UserAgentProfile.SAFARI) {
            context.getString(R.string.settings_user_agent_safari)
        } else {
            context.getString(R.string.settings_user_agent_android)
        }
        val uaRow = builder.createSettingRow(
            title = context.getString(R.string.settings_user_agent),
            statusText = uaStatusText,
            iconRes = R.drawable.devices_other_24px
        ) {
            val uaOptions = arrayOf(
                context.getString(R.string.settings_user_agent_android),
                context.getString(R.string.settings_user_agent_safari)
            )
            val selectedIndex = if (currentProfile == UserAgentProfile.SAFARI) 1 else 0
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select User Agent")
                .setSingleChoiceItems(uaOptions, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val selectedProfile = if (which == 1) UserAgentProfile.SAFARI else UserAgentProfile.ANDROID_CHROME
                    if (selectedProfile != currentProfile) {
                        BrowserPreferences.setUserAgentProfile(context, selectedProfile)
                    }
                }
                .show()
        }
        inner.addView(uaRow)

        card.addView(inner)
        return card
    }
}
