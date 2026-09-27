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
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object StartupSettingsSection {

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
                context.getString(R.string.settings_startup),
                R.drawable.refresh_24px,
                bottomPaddingDp = 8
            )
        )

        val homePageSet = !BrowserPreferences.getHomePageUrl(context).isNullOrBlank()

        val restoreTabsRow = builder.createSettingSwitchRow(
            title = context.getString(R.string.settings_restore_tabs_on_launch),
            description = if (homePageSet) {
                context.getString(R.string.settings_restore_tabs_home_override)
            } else {
                context.getString(R.string.settings_restore_tabs_on_launch_description)
            },
            iconRes = R.drawable.refresh_24px,
            isCheckedValue = BrowserPreferences.shouldRestoreTabsOnLaunch(context),
            isEnabledValue = !homePageSet
        ) { isChecked ->
            BrowserPreferences.setRestoreTabsOnLaunch(context, isChecked)
        }
        inner.addView(restoreTabsRow)

        val resumePageRow = builder.createSettingSwitchRow(
            title = context.getString(R.string.settings_resume_last_page_on_launch),
            description = if (homePageSet) {
                context.getString(R.string.settings_resume_last_page_home_override)
            } else {
                context.getString(R.string.settings_resume_last_page_on_launch_description)
            },
            iconRes = R.drawable.refresh_24px,
            isCheckedValue = BrowserPreferences.shouldResumeLastPageOnLaunch(context),
            isEnabledValue = !homePageSet
        ) { isChecked ->
            BrowserPreferences.setResumeLastPageOnLaunch(context, isChecked)
        }
        inner.addView(resumePageRow)

        card.addView(inner)
        return card
    }
}
