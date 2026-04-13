package com.kododake.aabrowser.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.model.UserAgentProfile

data class SettingsCallbacks(
    val onClose: () -> Unit = {},
    val onThemeChanged: () -> Unit = {},
    val onPageDarkeningChanged: () -> Unit = {},
    val onScaleChanged: () -> Unit = {},
    val onHomePageChanged: () -> Unit = {},
    val onInAppControlsChanged: () -> Unit = {},
    val onPickStartPageBackground: (() -> Unit)? = null,
    val onClearStartPageBackground: (() -> Unit)? = null
)

object SettingsViews {
    fun createSettingsContent(
        context: Context,
        includeDragHandle: Boolean = true,
        callbacks: SettingsCallbacks = SettingsCallbacks()
    ): View {
        fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

        fun getColorFromAttr(attrResId: Int): Int {
            val tv = TypedValue()
            context.theme.resolveAttribute(attrResId, tv, true)
            return tv.data
        }

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
                setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setIconResource(iconRes)
                iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_size_small)
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

        val smallIconSize = context.resources.getDimensionPixelSize(R.dimen.icon_size_small)
        val onSurfaceColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        if (includeDragHandle) {
            val handleFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(12), 0, dp(16))
            }
            handleFrame.addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(5)).apply { gravity = Gravity.CENTER }
                setBackgroundResource(R.drawable.drag_handle_background)
            })
            container.addView(handleFrame)
        }

        val headerCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))
            strokeWidth = dp(1)
            strokeColor = getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
        }
        val headerInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(context).apply {
            id = R.id.settingsHeaderTitle
            text = context.getString(R.string.settings_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(onSurfaceColor)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(context).apply {
            text = context.getString(R.string.settings_subtitle)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
        })
        val backBtn = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.buttonSettingsBack
            text = context.getString(R.string.menu_back)
            setTextColor(onSurfaceColor)
            setIconResource(R.drawable.arrow_back_24px)
            iconTint = ColorStateList.valueOf(onSurfaceColor)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = smallIconSize
            iconPadding = dp(8)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        headerInner.addView(titleCol)
        headerInner.addView(backBtn)
        headerCard.addView(headerInner)
        container.addView(headerCard)

        val appearanceCard = createStyledCard()
        val appearanceInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        appearanceInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_appearance),
                R.drawable.settings_24px,
                bottomPaddingDp = 4
            )
        )
        appearanceInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_appearance_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val themeMode = BrowserPreferences.getThemeMode(context)
        val themeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val autoThemeButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_theme_auto)
        }
        val lightThemeButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_theme_light)
        }
        val darkThemeButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_theme_dark)
        }
        themeGroup.addView(autoThemeButton)
        themeGroup.addView(lightThemeButton)
        themeGroup.addView(darkThemeButton)
        appearanceInner.addView(themeGroup)

        when (themeMode) {
            AppThemeMode.AUTO -> themeGroup.check(autoThemeButton.id)
            AppThemeMode.LIGHT -> themeGroup.check(lightThemeButton.id)
            AppThemeMode.DARK -> themeGroup.check(darkThemeButton.id)
        }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                lightThemeButton.id -> AppThemeMode.LIGHT
                darkThemeButton.id -> AppThemeMode.DARK
                else -> AppThemeMode.AUTO
            }
            if (selectedMode == BrowserPreferences.getThemeMode(context)) return@setOnCheckedChangeListener
            BrowserPreferences.setThemeMode(context, selectedMode)
            callbacks.onThemeChanged()
        }

        val betaDarkRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val betaDarkText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }
        betaDarkText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_beta_dark_pages)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(onSurfaceColor)
        })
        betaDarkText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_beta_dark_pages_description)
            setPadding(0, dp(4), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        val betaDarkSwitch = SwitchMaterial(context).apply {
            isChecked = BrowserPreferences.isBetaForceDarkPagesEnabled(context)
            setUseMaterialThemeColors(true)
        }
        betaDarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setBetaForceDarkPagesEnabled(context, isChecked)
            callbacks.onPageDarkeningChanged()
        }
        betaDarkRow.addView(betaDarkText)
        betaDarkRow.addView(betaDarkSwitch)
        appearanceInner.addView(betaDarkRow)

        appearanceCard.addView(appearanceInner)
        container.addView(appearanceCard)

        val displayScaleCard = createStyledCard()
        val displayScaleInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        displayScaleInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_display_scale),
                R.drawable.computer_24,
                bottomPaddingDp = 4
            )
        )
        displayScaleInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_display_scale_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val presetOptions = listOf(85, 100, 115, 130, 150)
        val currentScale = BrowserPreferences.getGlobalScalePercent(context)
        val scaleGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val presetButtons = mutableMapOf<Int, MaterialRadioButton>()
        presetOptions.forEach { percent ->
            val radioButton = MaterialRadioButton(context).apply {
                id = View.generateViewId()
                text = context.getString(R.string.settings_scale_option, percent)
            }
            presetButtons[percent] = radioButton
            scaleGroup.addView(radioButton)
        }
        val customScaleButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_scale_custom_option)
        }
        scaleGroup.addView(customScaleButton)
        displayScaleInner.addView(scaleGroup)

        val customScaleInputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            hint = context.getString(R.string.settings_scale_custom_hint)
            helperText = context.getString(
                R.string.settings_scale_custom_helper,
                BrowserPreferences.MIN_GLOBAL_SCALE_PERCENT,
                BrowserPreferences.MAX_GLOBAL_SCALE_PERCENT
            )
        }
        val customScaleInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentScale.toString())
        }
        customScaleInputLayout.addView(customScaleInput)
        displayScaleInner.addView(customScaleInputLayout)

        val applyScaleButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            text = context.getString(R.string.settings_scale_apply)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        displayScaleInner.addView(applyScaleButton)

        fun refreshCustomScaleState() {
            val customSelected = scaleGroup.checkedRadioButtonId == customScaleButton.id
            customScaleInputLayout.isEnabled = customSelected
            customScaleInput.isEnabled = customSelected
            applyScaleButton.isEnabled = customSelected
            customScaleInputLayout.alpha = if (customSelected) 1f else 0.6f
        }

        if (currentScale in presetOptions) {
            scaleGroup.check(presetButtons.getValue(currentScale).id)
        } else {
            scaleGroup.check(customScaleButton.id)
        }
        refreshCustomScaleState()

        scaleGroup.setOnCheckedChangeListener { _, checkedId ->
            refreshCustomScaleState()
            val preset = presetOptions.firstOrNull { presetButtons[it]?.id == checkedId } ?: return@setOnCheckedChangeListener
            if (preset == BrowserPreferences.getGlobalScalePercent(context)) return@setOnCheckedChangeListener
            BrowserPreferences.setGlobalScalePercent(context, preset)
            callbacks.onScaleChanged()
        }

        applyScaleButton.setOnClickListener {
            val entered = customScaleInput.text?.toString()?.trim().orEmpty()
            val value = entered.toIntOrNull()
            if (value == null) {
                customScaleInputLayout.error = context.getString(R.string.settings_scale_invalid)
                return@setOnClickListener
            }

            val sanitized = BrowserPreferences.sanitizeGlobalScalePercent(value)
            customScaleInputLayout.error = null
            customScaleInput.setText(sanitized.toString())
            if (sanitized == BrowserPreferences.getGlobalScalePercent(context)) return@setOnClickListener
            BrowserPreferences.setGlobalScalePercent(context, sanitized)
            callbacks.onScaleChanged()
        }

        displayScaleCard.addView(displayScaleInner)
        container.addView(displayScaleCard)

        val homePageCard = createStyledCard()
        val homePageInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        homePageInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_home_page),
                R.drawable.home_24px,
                bottomPaddingDp = 4
            )
        )
        homePageInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_home_page_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val currentHomePage = BrowserPreferences.getHomePageUrl(context)
        homePageInner.addView(TextView(context).apply {
            text = if (currentHomePage.isNullOrBlank()) {
                context.getString(R.string.settings_home_page_inactive)
            } else {
                context.getString(R.string.settings_home_page_active, currentHomePage)
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })

        val homePageInputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            hint = context.getString(R.string.settings_home_page_hint)
            helperText = context.getString(R.string.settings_home_page_helper)
        }
        val homePageInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(currentHomePage.orEmpty())
        }
        homePageInputLayout.addView(homePageInput)
        homePageInner.addView(homePageInputLayout)

        val homePageButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val saveHomePageButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            text = context.getString(R.string.settings_home_page_apply)
            setIconResource(R.drawable.home_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        val clearHomePageButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.settings_home_page_clear)
            setIconResource(R.drawable.delete_forever_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            isEnabled = !currentHomePage.isNullOrBlank()
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }
        homePageButtons.addView(saveHomePageButton)
        homePageButtons.addView(clearHomePageButton)
        homePageInner.addView(homePageButtons)

        saveHomePageButton.setOnClickListener {
            val entered = homePageInput.text?.toString()?.trim().orEmpty()
            if (entered.isBlank()) {
                homePageInputLayout.error = context.getString(R.string.settings_home_page_invalid)
                return@setOnClickListener
            }

            BrowserPreferences.setHomePageUrl(context, entered)
            val savedHomePage = BrowserPreferences.getHomePageUrl(context)
            if (savedHomePage.isNullOrBlank()) {
                homePageInputLayout.error = context.getString(R.string.settings_home_page_invalid)
                return@setOnClickListener
            }

            homePageInputLayout.error = null
            homePageInput.setText(savedHomePage)
            callbacks.onHomePageChanged()
            Toast.makeText(context, R.string.home_page_set, Toast.LENGTH_SHORT).show()
        }

        clearHomePageButton.setOnClickListener {
            BrowserPreferences.clearHomePageUrl(context)
            callbacks.onHomePageChanged()
            Toast.makeText(context, R.string.home_page_cleared, Toast.LENGTH_SHORT).show()
        }

        homePageCard.addView(homePageInner)
        container.addView(homePageCard)

        val startupCard = createStyledCard()
        val startupInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        startupInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_startup),
                R.drawable.refresh_24px,
                bottomPaddingDp = 4
            )
        )
        startupInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_startup_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val homePageSet = !BrowserPreferences.getHomePageUrl(context).isNullOrBlank()
        fun createStartupToggleRow(
            title: String,
            description: String,
            checked: Boolean,
            onChanged: (Boolean) -> Unit
        ): LinearLayout {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(12)
                }
            }
            textColumn.addView(TextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(onSurfaceColor)
            })
            textColumn.addView(TextView(context).apply {
                text = description
                setPadding(0, dp(4), 0, 0)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            val switch = SwitchMaterial(context).apply {
                isChecked = checked
                isEnabled = !homePageSet
                alpha = if (isEnabled) 1f else 0.6f
                setUseMaterialThemeColors(true)
            }
            switch.setOnCheckedChangeListener { _, isCheckedValue ->
                onChanged(isCheckedValue)
            }
            row.addView(textColumn)
            row.addView(switch)
            return row
        }

        startupInner.addView(
            createStartupToggleRow(
                title = context.getString(R.string.settings_restore_tabs_on_launch),
                description = if (homePageSet) {
                    context.getString(R.string.settings_restore_tabs_home_override)
                } else {
                    context.getString(R.string.settings_restore_tabs_on_launch_description)
                },
                checked = BrowserPreferences.shouldRestoreTabsOnLaunch(context),
                onChanged = { BrowserPreferences.setRestoreTabsOnLaunch(context, it) }
            )
        )

        startupInner.addView(
            createStartupToggleRow(
                title = context.getString(R.string.settings_resume_last_page_on_launch),
                description = if (homePageSet) {
                    context.getString(R.string.settings_resume_last_page_home_override)
                } else {
                    context.getString(R.string.settings_resume_last_page_on_launch_description)
                },
                checked = BrowserPreferences.shouldResumeLastPageOnLaunch(context),
                onChanged = { BrowserPreferences.setResumeLastPageOnLaunch(context, it) }
            )
        )
        startupCard.addView(startupInner)
        container.addView(startupCard)

        val inAppControlsCard = createStyledCard()
        val inAppControlsInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        inAppControlsInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_in_app_controls),
                R.drawable.search_24px,
                bottomPaddingDp = 4
            )
        )
        inAppControlsInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_in_app_controls_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val alwaysShowUrlBarRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val alwaysShowUrlBarText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }
        alwaysShowUrlBarText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_always_show_url_bar)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(onSurfaceColor)
        })
        alwaysShowUrlBarText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_always_show_url_bar_description)
            setPadding(0, dp(4), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        val alwaysShowUrlBarSwitch = SwitchMaterial(context).apply {
            isChecked = BrowserPreferences.shouldAlwaysShowUrlBar(context)
            setUseMaterialThemeColors(true)
        }
        alwaysShowUrlBarSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setAlwaysShowUrlBar(context, isChecked)
            callbacks.onInAppControlsChanged()
        }
        alwaysShowUrlBarRow.addView(alwaysShowUrlBarText)
        alwaysShowUrlBarRow.addView(alwaysShowUrlBarSwitch)
        inAppControlsInner.addView(alwaysShowUrlBarRow)

        inAppControlsInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_quick_action_button_mode)
            setPadding(0, dp(16), 0, dp(6))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(onSurfaceColor)
        })
        val quickActionModeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val menuActionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_mode_menu)
        }
        val addressBarActionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_mode_address_bar)
        }
        quickActionModeGroup.addView(menuActionButton)
        quickActionModeGroup.addView(addressBarActionButton)
        inAppControlsInner.addView(quickActionModeGroup)

        when (BrowserPreferences.getQuickActionButtonMode(context)) {
            QuickActionButtonMode.MENU -> quickActionModeGroup.check(menuActionButton.id)
            QuickActionButtonMode.ADDRESS_BAR -> quickActionModeGroup.check(addressBarActionButton.id)
        }

        quickActionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = if (checkedId == addressBarActionButton.id) {
                QuickActionButtonMode.ADDRESS_BAR
            } else {
                QuickActionButtonMode.MENU
            }
            if (selectedMode == BrowserPreferences.getQuickActionButtonMode(context)) return@setOnCheckedChangeListener
            BrowserPreferences.setQuickActionButtonMode(context, selectedMode)
            callbacks.onInAppControlsChanged()
        }

        val alwaysShowButtonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val alwaysShowButtonText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }
        alwaysShowButtonText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_quick_action_button_always_visible)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(onSurfaceColor)
        })
        alwaysShowButtonText.addView(TextView(context).apply {
            text = context.getString(R.string.settings_quick_action_button_always_visible_description)
            setPadding(0, dp(4), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        val alwaysShowButtonSwitch = SwitchMaterial(context).apply {
            isChecked = BrowserPreferences.isQuickActionButtonAlwaysVisible(context)
            setUseMaterialThemeColors(true)
        }
        alwaysShowButtonSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setQuickActionButtonAlwaysVisible(context, isChecked)
            callbacks.onInAppControlsChanged()
        }
        alwaysShowButtonRow.addView(alwaysShowButtonText)
        alwaysShowButtonRow.addView(alwaysShowButtonSwitch)
        inAppControlsInner.addView(alwaysShowButtonRow)

        inAppControlsInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_quick_action_button_position)
            setPadding(0, dp(16), 0, dp(6))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(onSurfaceColor)
        })
        val quickActionPositionGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val bottomLeftPositionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_position_bottom_left)
        }
        val bottomRightPositionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_position_bottom_right)
        }
        val topLeftPositionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_position_top_left)
        }
        val topRightPositionButton = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_quick_action_button_position_top_right)
        }
        quickActionPositionGroup.addView(bottomLeftPositionButton)
        quickActionPositionGroup.addView(bottomRightPositionButton)
        quickActionPositionGroup.addView(topLeftPositionButton)
        quickActionPositionGroup.addView(topRightPositionButton)
        inAppControlsInner.addView(quickActionPositionGroup)

        when (BrowserPreferences.getQuickActionButtonPosition(context)) {
            QuickActionButtonPosition.BOTTOM_LEFT -> quickActionPositionGroup.check(bottomLeftPositionButton.id)
            QuickActionButtonPosition.BOTTOM_RIGHT -> quickActionPositionGroup.check(bottomRightPositionButton.id)
            QuickActionButtonPosition.TOP_LEFT -> quickActionPositionGroup.check(topLeftPositionButton.id)
            QuickActionButtonPosition.TOP_RIGHT -> quickActionPositionGroup.check(topRightPositionButton.id)
        }

        quickActionPositionGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedPosition = when (checkedId) {
                bottomRightPositionButton.id -> QuickActionButtonPosition.BOTTOM_RIGHT
                topLeftPositionButton.id -> QuickActionButtonPosition.TOP_LEFT
                topRightPositionButton.id -> QuickActionButtonPosition.TOP_RIGHT
                else -> QuickActionButtonPosition.BOTTOM_LEFT
            }
            if (selectedPosition == BrowserPreferences.getQuickActionButtonPosition(context)) return@setOnCheckedChangeListener
            BrowserPreferences.setQuickActionButtonPosition(context, selectedPosition)
            callbacks.onInAppControlsChanged()
        }

        inAppControlsCard.addView(inAppControlsInner)
        container.addView(inAppControlsCard)

        val startPageCard = createStyledCard()
        val startPageInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        startPageInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_start_page),
                R.drawable.kid_star_24px,
                bottomPaddingDp = 4
            )
        )
        startPageInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_start_page_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })

        val startPageCount = BrowserPreferences.getStartPageSites(context).size
        startPageInner.addView(TextView(context).apply {
            text = context.getString(
                R.string.settings_start_page_count,
                startPageCount,
                BrowserPreferences.MAX_START_PAGE_SITES
            )
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })

        val backgroundStatus = BrowserPreferences.getStartPageBackgroundUri(context)
        startPageInner.addView(TextView(context).apply {
            text = if (backgroundStatus.isNullOrBlank()) {
                context.getString(R.string.settings_start_page_background_default)
            } else {
                context.getString(R.string.settings_start_page_background_custom)
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(8), 0, 0)
        })

        val startPageButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val chooseBackgroundButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            text = context.getString(R.string.settings_start_page_choose_background)
            setIconResource(R.drawable.search_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            isEnabled = callbacks.onPickStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        val clearBackgroundButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.settings_start_page_clear_background)
            setIconResource(R.drawable.delete_forever_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            isEnabled = !backgroundStatus.isNullOrBlank() && callbacks.onClearStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }
        startPageButtons.addView(chooseBackgroundButton)
        startPageButtons.addView(clearBackgroundButton)
        startPageInner.addView(startPageButtons)
        startPageCard.addView(startPageInner)
        container.addView(startPageCard)

        val uaCard = createStyledCard()
        val uaInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        uaInner.addView(createSectionTitle(context.getString(R.string.settings_user_agent), R.drawable.devices_other_24px, bottomPaddingDp = 4))
        val uaGroup = RadioGroup(context).apply {
            id = R.id.userAgentGroup
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val androidUaButton = MaterialRadioButton(context).apply {
            id = R.id.userAgentAndroid
            text = context.getString(R.string.settings_user_agent_android)
        }
        val safariUaButton = MaterialRadioButton(context).apply {
            id = R.id.userAgentSafari
            text = context.getString(R.string.settings_user_agent_safari)
        }
        uaGroup.addView(androidUaButton)
        uaGroup.addView(safariUaButton)
        uaInner.addView(uaGroup)
        uaCard.addView(uaInner)
        container.addView(uaCard)

        val donateCard = createStyledCard()
        val donateInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        donateInner.addView(createSectionTitle(context.getString(R.string.settings_donate), R.drawable.volunteer_activism_24px, bottomPaddingDp = 4))
        donateInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_donate_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, 0)
        })
        val sponsorUrl = "https://github.com/sponsors/kododake"

        fun generateQrBitmap(data: String, sizePx: Int): Bitmap? {
            return runCatching {
                val matrix: BitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
                Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    for (x in 0 until sizePx) {
                        for (y in 0 until sizePx) {
                            setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                }
            }.getOrNull()
        }

        val donateTabGroup = MaterialButtonToggleGroup(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            isSingleSelection = true
            isSelectionRequired = true
        }
        val githubTabButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_donate_tab_github)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bitcoinTabButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = context.getString(R.string.settings_donate_tab_bitcoin)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        donateTabGroup.addView(githubTabButton)
        donateTabGroup.addView(bitcoinTabButton)
        donateInner.addView(donateTabGroup)

        val donateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val donateQrImage = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100))
            setPadding(dp(1), dp(1), dp(1), dp(1))
            setBackgroundColor(Color.WHITE)
        }
        donateRow.addView(donateQrImage)
        val donateCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
        }
        val donateAddressView = TextView(context).apply {
            id = R.id.bitcoinAddress
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(onSurfaceColor)
        }
        val donateActionButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            id = R.id.copyBitcoinButton
            iconSize = smallIconSize
            iconPadding = dp(8)
            backgroundTintList = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
            isClickable = true
            isFocusable = true
        }
        donateCol.addView(donateAddressView)
        donateCol.addView(donateActionButton)
        donateRow.addView(donateCol)
        donateInner.addView(donateRow)

        fun applyDonateTab(isGithub: Boolean) {
            if (isGithub) {
                donateAddressView.text = sponsorUrl
                val qrBitmap = generateQrBitmap(sponsorUrl, dp(100))
                if (qrBitmap != null) {
                    donateQrImage.setImageBitmap(qrBitmap)
                } else {
                    donateQrImage.setImageResource(R.drawable.ic_github)
                }
                donateActionButton.text = context.getString(R.string.settings_donate_open_github_sponsors)
                donateActionButton.setIconResource(R.drawable.favorite_24px)
                val pink = ColorStateList.valueOf(Color.parseColor("#EC407A"))
                donateActionButton.iconTint = pink
                donateActionButton.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sponsorUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                donateAddressView.text = context.getString(R.string.donate_bitcoin_address_value)
                donateQrImage.setImageResource(R.drawable.bitcoin_qr)
                donateActionButton.text = context.getString(R.string.donate_copy)
                donateActionButton.setIconResource(R.drawable.content_copy_24px)
                val onSecondary = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
                donateActionButton.iconTint = onSecondary
                donateActionButton.setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin Address", donateAddressView.text.toString()))
                    Toast.makeText(context, R.string.donate_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }

        donateTabGroup.check(githubTabButton.id)
        applyDonateTab(isGithub = true)
        donateTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            applyDonateTab(isGithub = checkedId == githubTabButton.id)
        }

        donateCard.addView(donateInner)
        container.addView(donateCard)

        val donorsCard = createStyledCard()
        val donorsInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        donorsInner.addView(createSectionTitle(context.getString(R.string.settings_donors), R.drawable.favorite_24px, bottomPaddingDp = 4))
        donorsInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_donors_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, 0)
        })
        donorsCard.addView(donorsInner)
        container.addView(donorsCard)

        val siteDataCard = createStyledCard()
        val siteDataInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        siteDataInner.addView(createSectionTitle(context.getString(R.string.settings_site_data_title), R.drawable.security_24px, bottomPaddingDp = 4))
        siteDataInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_site_data_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })
        val clearSitePermissionsButton = createListButton(
            R.id.buttonClearSitePermissions,
            context.getString(R.string.settings_clear_site_permissions),
            R.drawable.lock_reset_24px
        )
        val clearCookiesButton = createListButton(
            R.id.buttonClearCookies,
            context.getString(R.string.settings_clear_cookies),
            R.drawable.delete_forever_24px
        )
        siteDataInner.addView(clearSitePermissionsButton)
        siteDataInner.addView(clearCookiesButton)
        siteDataCard.addView(siteDataInner)
        container.addView(siteDataCard)

        val licenseCard = createStyledCard()
        val licenseInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        licenseInner.addView(createSectionTitle(context.getString(R.string.settings_license), R.drawable.gplv3, iconWidthDp = 48, iconHeightDp = 24, tintIcon = false, bottomPaddingDp = 8))
        licenseInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_license_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, 0, 0, dp(8))
        })
        val viewKododakeButton = createListButton(R.id.ViewKododakeButton, context.getString(R.string.kododake_name), R.drawable.ic_github)
        val viewLicenseButton = createListButton(R.id.viewLicenseButton, context.getString(R.string.settings_license), R.drawable.info_24px)
        val viewOssLicensesButton = createListButton(R.id.viewOssLicensesButton, context.getString(R.string.open_source_view_licenses), R.drawable.search_24px)
        licenseInner.addView(viewKododakeButton)
        licenseInner.addView(viewLicenseButton)
        licenseInner.addView(viewOssLicensesButton)
        licenseCard.addView(licenseInner)
        container.addView(licenseCard)

        backBtn.setOnClickListener { callbacks.onClose() }

        val currentProfile = BrowserPreferences.getUserAgentProfile(context)
        if (currentProfile == UserAgentProfile.SAFARI) {
            uaGroup.check(safariUaButton.id)
        } else {
            uaGroup.check(androidUaButton.id)
        }
        uaGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = if (checkedId == safariUaButton.id) UserAgentProfile.SAFARI else UserAgentProfile.ANDROID_CHROME
            BrowserPreferences.setUserAgentProfile(context, profile)
        }

        chooseBackgroundButton.setOnClickListener {
            callbacks.onPickStartPageBackground?.invoke()
        }

        clearBackgroundButton.setOnClickListener {
            callbacks.onClearStartPageBackground?.invoke()
        }

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

        clearSitePermissionsButton.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_site_permissions_title),
                message = context.getString(R.string.settings_clear_site_permissions_message)
            ) {
                BrowserPreferences.clearSavedSitePermissions(context)
                showSuccessDialog(
                    title = context.getString(R.string.settings_clear_site_permissions_success_title),
                    message = context.getString(R.string.settings_clear_site_permissions_success_message)
                )
            }
        }

        clearCookiesButton.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_cookies_title),
                message = context.getString(R.string.settings_clear_cookies_message)
            ) {
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(context).apply {
                    clearHttpAuthUsernamePassword()
                }
                runCatching { context.deleteDatabase("webview.db") }
                runCatching { context.deleteDatabase("webviewCache.db") }
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    showSuccessDialog(
                        title = context.getString(R.string.settings_clear_cookies_success_title),
                        message = context.getString(R.string.settings_clear_cookies_success_message)
                    )
                }
            }
        }

        return container
    }

    fun createSettingsActivityView(context: Context): View = createSettingsContent(
        context = context,
        includeDragHandle = false,
        callbacks = SettingsCallbacks(
            onClose = { (context as? android.app.Activity)?.finish() },
            onThemeChanged = { (context as? android.app.Activity)?.recreate() },
            onPageDarkeningChanged = { (context as? android.app.Activity)?.recreate() },
            onScaleChanged = { (context as? android.app.Activity)?.recreate() },
            onHomePageChanged = { (context as? android.app.Activity)?.recreate() }
        )
    )
}
