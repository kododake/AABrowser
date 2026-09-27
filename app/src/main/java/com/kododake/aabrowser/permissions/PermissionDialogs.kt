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

package com.kododake.aabrowser.permissions

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.screens.dialogs.DialogViews

class PermissionDialogs(private val activity: AppCompatActivity) {

    companion object {
        const val DIALOG_TYPE_CLEARTEXT = 0
        const val DIALOG_TYPE_MICROPHONE = 1
        const val DIALOG_TYPE_LOCATION = 2
    }

    var isShowingCleartextDialog: Boolean = false
    var isShowingMicrophoneDialog: Boolean = false
    var isShowingLocationDialog: Boolean = false
    private var activeDialog: AlertDialog? = null

    init {
        activity.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                activeDialog?.let { dialog ->
                    if (dialog.isShowing) {
                        applyDialogWindowBounds(dialog)
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        })
    }

    fun showLocationUpgradeDialog(
        origin: Uri?,
        onUpgrade: () -> Unit,
        onKeepApproximate: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        if (isShowingLocationDialog) return
        isShowingLocationDialog = true

        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.location_access_unknown_origin)
        var dialog: AlertDialog? = null
        val view = DialogViews.createConfirmationDialogView(
            context = activity,
            title = activity.getString(R.string.location_upgrade_title),
            message = activity.getString(R.string.location_upgrade_message),
            hostLabel = activity.getString(R.string.location_access_host_label),
            hostValue = originLabel,
            isDestructive = true,
            confirmButtonText = activity.getString(R.string.location_upgrade_btn_precise),
            onConfirm = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onUpgrade()
            },
            secondaryButtonText = activity.getString(R.string.location_upgrade_btn_approximate),
            onSecondary = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onKeepApproximate()
            },
            dismissButtonText = activity.getString(android.R.string.cancel),
            onDismiss = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onCancel()
            }
        )

        dialog = showDialog(view) {
            isShowingLocationDialog = false
            onCancel()
        }
        dialog?.setOnDismissListener {
            isShowingLocationDialog = false
            if (activeDialog == dialog) activeDialog = null
        }
    }

    fun showLocationAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.location_access_unknown_origin)
        showSitePermissionDialog(
            title = activity.getString(R.string.location_access_title),
            message = activity.getString(R.string.location_access_message),
            dialogType = DIALOG_TYPE_LOCATION,
            hostLabel = activity.getString(R.string.location_access_host_label),
            hostValue = originLabel,
            detailMessage = activity.getString(R.string.location_access_detail),
            onAllowOnce = onAllowOnce,
            onAllowHost = if (origin?.host.isNullOrBlank() || !isSecure) null else onAllowHost,
            onCancel = onCancel
        )
    }

    fun showMicrophoneAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.microphone_access_unknown_origin)
        showSitePermissionDialog(
            title = activity.getString(R.string.microphone_access_title),
            message = activity.getString(R.string.microphone_access_message),
            dialogType = DIALOG_TYPE_MICROPHONE,
            hostLabel = activity.getString(R.string.microphone_access_host_label),
            hostValue = originLabel,
            detailMessage = activity.getString(R.string.microphone_access_detail),
            onAllowOnce = onAllowOnce,
            onAllowHost = if (origin?.host.isNullOrBlank() || !isSecure) null else onAllowHost,
            onCancel = onCancel
        )
    }

    fun showCleartextNavigationDialog(
        uri: Uri,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val host = uri.host ?: uri.toString()
        showSitePermissionDialog(
            title = activity.getString(R.string.cleartext_connection_title),
            message = activity.getString(R.string.cleartext_connection_message, host),
            dialogType = DIALOG_TYPE_CLEARTEXT,
            onAllowOnce = onAllowOnce,
            onAllowHost = onAllowHost,
            onCancel = onCancel
        )
    }

    private fun showSitePermissionDialog(
        title: String,
        message: String,
        dialogType: Int,
        hostLabel: String? = null,
        hostValue: String? = null,
        detailMessage: String? = null,
        onAllowOnce: () -> Unit,
        onAllowHost: (() -> Unit)?,
        onCancel: () -> Unit
    ) {
        val flagAccessor: () -> Boolean = {
            when (dialogType) {
                DIALOG_TYPE_MICROPHONE -> isShowingMicrophoneDialog
                DIALOG_TYPE_LOCATION -> isShowingLocationDialog
                else -> isShowingCleartextDialog
            }
        }
        val flagSetter: (Boolean) -> Unit = { showing ->
            when (dialogType) {
                DIALOG_TYPE_MICROPHONE -> isShowingMicrophoneDialog = showing
                DIALOG_TYPE_LOCATION -> isShowingLocationDialog = showing
                else -> isShowingCleartextDialog = showing
            }
        }

        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        if (flagAccessor()) return
        flagSetter(true)

        var dialog: AlertDialog? = null
        val view = DialogViews.createConfirmationDialogView(
            context = activity,
            title = title,
            message = message,
            hostLabel = hostLabel,
            hostValue = hostValue,
            detailMessage = detailMessage,
            isDestructive = (dialogType == DIALOG_TYPE_CLEARTEXT || dialogType == DIALOG_TYPE_LOCATION),
            confirmButtonText = if (onAllowHost != null) activity.getString(R.string.cleartext_allow_host) else null,
            onConfirm = if (onAllowHost != null) {
                {
                    try { dialog?.dismiss() } catch (_: Exception) {}
                    onAllowHost()
                }
            } else null,
            secondaryButtonText = activity.getString(R.string.cleartext_allow_once),
            onSecondary = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onAllowOnce()
            },
            dismissButtonText = activity.getString(android.R.string.cancel),
            onDismiss = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                onCancel()
            }
        )

        dialog = showDialog(view) {
            flagSetter(false)
            onCancel()
        }
        dialog?.setOnDismissListener {
            flagSetter(false)
            if (activeDialog == dialog) activeDialog = null
        }
    }

    private fun applyDialogWindowBounds(dialog: AlertDialog) {
        val window = dialog.window ?: return
        val metrics = activity.resources.displayMetrics
        val density = metrics.density
        val maxAllowedWidth = (560 * density).toInt()
        val calculatedWidth = (metrics.widthPixels * 0.9).toInt()
        val width = minOf(calculatedWidth, maxAllowedWidth)
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showDialog(view: View, onError: () -> Unit): AlertDialog? {
        return try {
            val dialog = MaterialAlertDialogBuilder(
                activity,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            ).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
            applyDialogWindowBounds(dialog)
            activeDialog = dialog
            dialog
        } catch (_: Exception) {
            onError()
            null
        }
    }
}
