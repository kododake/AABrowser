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

package com.kododake.aabrowser.web

import android.app.Activity
import android.net.Uri
import android.net.http.SslError
import android.view.WindowManager
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.screens.dialogs.DialogViews

object SslErrorHandlerHelper {

    private val allowedSslHosts = HashSet<String>()

    fun handleSslError(activity: Activity, handler: SslErrorHandler, error: SslError) {
        val url = error.url ?: ""
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

        if (host != null && allowedSslHosts.contains(host)) {
            handler.proceed()
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            handler.cancel()
            return
        }

        val primaryError = error.primaryError
        val errorDescription = getSslErrorDescription(activity, primaryError)
        val hostLabel = host ?: url

        var dialog: AlertDialog? = null
        val view = DialogViews.createConfirmationDialogView(
            context = activity,
            title = activity.getString(R.string.ssl_error_title),
            message = activity.getString(R.string.ssl_error_message, hostLabel),
            hostLabel = activity.getString(R.string.location_access_host_label),
            hostValue = hostLabel,
            detailMessage = activity.getString(R.string.ssl_error_reason_prefix) + " " + errorDescription,
            isDestructive = true,
            confirmButtonText = activity.getString(R.string.ssl_error_proceed),
            onConfirm = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                if (host != null) {
                    allowedSslHosts.add(host)
                }
                handler.proceed()
            },
            dismissButtonText = activity.getString(R.string.ssl_error_cancel),
            onDismiss = {
                try { dialog?.dismiss() } catch (_: Exception) {}
                handler.cancel()
            }
        )

        try {
            dialog = MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setView(view)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
            val width = (activity.resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            handler.cancel()
        }
    }

    private fun getSslErrorDescription(activity: Activity, errorCode: Int): String {
        return when (errorCode) {
            SslError.SSL_EXPIRED -> activity.getString(R.string.ssl_error_expired)
            SslError.SSL_IDMISMATCH -> activity.getString(R.string.ssl_error_idmismatch)
            SslError.SSL_UNTRUSTED -> activity.getString(R.string.ssl_error_untrusted)
            SslError.SSL_NOTYETVALID -> activity.getString(R.string.ssl_error_notyetvalid)
            SslError.SSL_DATE_INVALID -> activity.getString(R.string.ssl_error_date_invalid)
            else -> activity.getString(R.string.ssl_error_invalid)
        }
    }

    fun clearAllowedSslHosts() {
        allowedSslHosts.clear()
    }
}
