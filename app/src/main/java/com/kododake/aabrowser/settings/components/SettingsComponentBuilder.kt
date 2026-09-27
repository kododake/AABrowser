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

package com.kododake.aabrowser.settings.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.kododake.aabrowser.R

class SettingsComponentBuilder(private val context: Context) {

    fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    fun getColorFromAttr(attrResId: Int): Int {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(attrResId, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(context, tv.resourceId)
            }
            return tv.data
        }
        return Color.TRANSPARENT
    }

    val onSurfaceColor: Int get() = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
    val onSurfaceVariantColor: Int get() = getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
    val smallIconSize: Int get() = context.resources.getDimensionPixelSize(R.dimen.icon_size_small)

    fun createStyledCard(): MaterialCardView = MaterialCardView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        radius = dp(16).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
        setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))
    }

    fun createSectionTitle(
        titleText: String,
        iconRes: Int,
        iconWidthDp: Int = 20,
        iconHeightDp: Int = 20,
        tintIcon: Boolean = true,
        bottomPaddingDp: Int = 0
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (bottomPaddingDp > 0) {
                setPadding(0, 0, 0, dp(bottomPaddingDp))
            }

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(iconWidthDp), dp(iconHeightDp)).apply {
                    marginEnd = dp(10)
                }
                setImageResource(iconRes)
                if (tintIcon) {
                    imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            })

            addView(TextView(context).apply {
                text = titleText
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    fun createListButton(idRes: Int, textStr: String, iconRes: Int): MaterialButton {
        return MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = idRes
            text = textStr
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(onSurfaceColor)
            setIconResource(iconRes)
            iconSize = smallIconSize
            iconPadding = dp(12)
            iconTint = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTintMode = android.graphics.PorterDuff.Mode.SRC_IN
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            alpha = 1.0f
            isClickable = true
            isFocusable = true
        }
    }

    fun showSuccessDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showConfirmationDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_action_delete) { _, _ -> onConfirm() }
            .show()
    }

    fun createSettingRow(
        title: String,
        statusText: String,
        iconRes: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val onSurfaceColorVal = onSurfaceColor
        val onSurfaceVariantColorVal = onSurfaceVariantColor
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(12), dp(12), dp(12))

            val rippleColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(onSurfaceColorVal, 30)
            )
            val contentBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.TRANSPARENT)
            }
            val maskBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.WHITE)
            }
            background = android.graphics.drawable.RippleDrawable(rippleColor, contentBg, maskBg)

            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(16)
                }
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(onSurfaceColorVal)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(context).apply {
                text = statusText
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(onSurfaceVariantColorVal)
                setPadding(0, dp(2), 0, 0)
            })
            addView(textCol)

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setImageResource(R.drawable.arrow_forward_24px)
                imageTintList = ColorStateList.valueOf(onSurfaceVariantColorVal)
                alpha = 0.5f
            })
        }
    }

    fun createSettingSwitchRow(
        title: String,
        description: String,
        iconRes: Int,
        isCheckedValue: Boolean,
        isEnabledValue: Boolean = true,
        onCheckedChange: (Boolean) -> Unit
    ): LinearLayout {
        val onSurfaceColorVal = onSurfaceColor
        val onSurfaceVariantColorVal = onSurfaceVariantColor
        val switch = SwitchMaterial(context).apply {
            isChecked = isCheckedValue
            isEnabled = isEnabledValue
            setUseMaterialThemeColors(true)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = isEnabledValue
            isFocusable = isEnabledValue
            setPadding(dp(12), dp(12), dp(12), dp(12))

            if (isEnabledValue) {
                val rippleColor = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(onSurfaceColorVal, 30)
                )
                val contentBg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.TRANSPARENT)
                }
                val maskBg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.WHITE)
                }
                background = android.graphics.drawable.RippleDrawable(rippleColor, contentBg, maskBg)
                setOnClickListener {
                    switch.toggle()
                }
            } else {
                alpha = 0.6f
            }

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(16)
                }
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(onSurfaceColorVal)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(context).apply {
                text = description
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(onSurfaceVariantColorVal)
                setPadding(0, dp(2), 0, 0)
            })
            addView(textCol)

            switch.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
            addView(switch)
        }
        return row
    }
}
