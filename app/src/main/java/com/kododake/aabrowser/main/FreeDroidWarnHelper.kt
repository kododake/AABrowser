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

package com.kododake.aabrowser.main

import android.app.Activity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.AppConstants.FREE_DROID_WARN_SOLUTIONS_URL
import com.kododake.aabrowser.AppConstants.KEEP_ANDROID_OPEN_URL
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.ui.compose.screens.dialogs.DialogViews
import org.woheller69.freeDroidWarn.R as FreeDroidWarnR

object FreeDroidWarnHelper {

    fun checkAndShow(
        activity: Activity,
        errorColor: Int,
        onNavigate: (String) -> Unit
    ) {
        if (BrowserPreferences.isKeepAndroidOpenAcknowledged(activity)) return

        var dialog: AlertDialog? = null
        val view = DialogViews.createConfirmationDialogView(
            context = activity,
            title = activity.getString(android.R.string.dialog_alert_title),
            message = activity.getString(FreeDroidWarnR.string.dialog_Warning),
            isDestructive = false,
            confirmButtonText = activity.getString(android.R.string.ok),
            onConfirm = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                BrowserPreferences.setKeepAndroidOpenAcknowledged(activity, true)
            },
            secondaryButtonText = activity.getString(FreeDroidWarnR.string.solution),
            onSecondary = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onNavigate(FREE_DROID_WARN_SOLUTIONS_URL)
            },
            dismissButtonText = activity.getString(FreeDroidWarnR.string.dialog_more_info),
            onDismiss = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onNavigate(KEEP_ANDROID_OPEN_URL)
            }
        )

        try {
            dialog = MaterialAlertDialogBuilder(
                activity,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            )
                .setView(view)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
            val width = (activity.resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {}
    }
}
