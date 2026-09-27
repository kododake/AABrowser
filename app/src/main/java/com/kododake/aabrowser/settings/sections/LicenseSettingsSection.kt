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
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kododake.aabrowser.R
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object LicenseSettingsSection {

    fun build(
        context: Context,
        builder: SettingsComponentBuilder
    ): View {
        val licenseCard = builder.createStyledCard()
        val licenseInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(builder.dp(16), builder.dp(12), builder.dp(16), builder.dp(16))
        }
        licenseInner.addView(
            builder.createSectionTitle(
                context.getString(R.string.settings_license),
                R.drawable.gplv3,
                iconWidthDp = 48,
                iconHeightDp = 24,
                tintIcon = false,
                bottomPaddingDp = 8
            )
        )
        licenseInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_license_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(builder.onSurfaceColor)
            setPadding(0, 0, 0, builder.dp(8))
        })
        val viewKododakeButton = builder.createListButton(
            R.id.ViewKododakeButton,
            context.getString(R.string.kododake_name),
            R.drawable.ic_github
        )
        val viewLicenseButton = builder.createListButton(
            R.id.viewLicenseButton,
            context.getString(R.string.settings_license),
            R.drawable.info_24px
        )
        val viewOssLicensesButton = builder.createListButton(
            R.id.viewOssLicensesButton,
            context.getString(R.string.open_source_view_licenses),
            R.drawable.search_24px
        )

        fun openUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        viewKododakeButton.setOnClickListener { openUrl("https://github.com/kododake") }
        viewLicenseButton.setOnClickListener { openUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
        viewOssLicensesButton.setOnClickListener {
            try {
                val activityClass = Class.forName("com.google.android.gms.oss.licenses.OssLicensesMenuActivity")
                val intent = Intent(context, activityClass)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        licenseInner.addView(viewKododakeButton)
        licenseInner.addView(viewLicenseButton)
        licenseInner.addView(viewOssLicensesButton)
        licenseCard.addView(licenseInner)

        return licenseCard
    }
}
