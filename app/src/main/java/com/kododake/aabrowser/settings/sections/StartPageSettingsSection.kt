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
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder

object StartPageSettingsSection {

    fun build(
        context: Context,
        builder: SettingsComponentBuilder,
        callbacks: SettingsCallbacks
    ): View {
        val card = builder.createStyledCard()
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(builder.dp(16), builder.dp(16), builder.dp(16), builder.dp(16))
        }
        inner.addView(
            builder.createSectionTitle(
                context.getString(R.string.settings_start_page),
                R.drawable.kid_star_24px,
                bottomPaddingDp = 8
            )
        )

        val startPageCount = BrowserPreferences.getStartPageSites(context).size
        val countRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, builder.dp(4), 0, builder.dp(4))

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(builder.dp(24), builder.dp(24)).apply {
                    marginEnd = builder.dp(16)
                }
                setImageResource(R.drawable.kid_star_24px)
                imageTintList = ColorStateList.valueOf(builder.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            })
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = "Quick Links"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(builder.onSurfaceColor)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(context).apply {
                text = context.getString(
                    R.string.settings_start_page_count,
                    startPageCount,
                    BrowserPreferences.MAX_START_PAGE_SITES
                )
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(builder.onSurfaceVariantColor)
                setPadding(0, builder.dp(2), 0, 0)
            })
            addView(textCol)
        }
        inner.addView(countRow)

        val backgroundStatus = BrowserPreferences.getStartPageBackgroundUri(context)
        val bgStatusText = if (backgroundStatus.isNullOrBlank()) {
            context.getString(R.string.settings_start_page_background_default)
        } else {
            context.getString(R.string.settings_start_page_background_custom)
        }

        inner.addView(TextView(context).apply {
            text = "Background Image"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(builder.onSurfaceColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, builder.dp(12), 0, builder.dp(2))
        })

        inner.addView(TextView(context).apply {
            text = bgStatusText
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(builder.onSurfaceVariantColor)
            setPadding(0, 0, 0, builder.dp(12))
        })

        val startPageButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, builder.dp(4), 0, 0)
        }
        val chooseBackgroundButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonTonalStyle
        ).apply {
            text = context.getString(R.string.settings_start_page_choose_background)
            setIconResource(R.drawable.search_24px)
            iconSize = builder.smallIconSize
            iconPadding = builder.dp(8)
            isEnabled = callbacks.onPickStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = builder.dp(8)
            }
            setOnClickListener {
                callbacks.onPickStartPageBackground?.invoke()
            }
        }
        val clearBackgroundButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = context.getString(R.string.settings_start_page_clear_background)
            setIconResource(R.drawable.delete_forever_24px)
            iconSize = builder.smallIconSize
            iconPadding = builder.dp(8)
            isEnabled = !backgroundStatus.isNullOrBlank() && callbacks.onClearStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = builder.dp(8)
            }
            setOnClickListener {
                callbacks.onClearStartPageBackground?.invoke()
            }
        }
        startPageButtons.addView(chooseBackgroundButton)
        startPageButtons.addView(clearBackgroundButton)
        inner.addView(startPageButtons)

        card.addView(inner)
        return card
    }
}
