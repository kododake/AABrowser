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

package com.kododake.aabrowser.ui.compose.screens.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme

object DialogViews {

    fun createQrCodeDialogView(
        context: Context,
        url: String,
        qrBitmap: Bitmap?,
        onShare: () -> Unit,
        onDismiss: () -> Unit
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AABrowserTheme {
                    QrCodeDialog(
                        url = url,
                        qrBitmap = qrBitmap,
                        onShare = onShare,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }

    fun createVersionCheckDialogView(
        context: Context,
        isChecking: Boolean,
        latestVersion: String?,
        releaseUrl: String?,
        onOpenRelease: (String) -> Unit,
        onDismiss: () -> Unit
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AABrowserTheme {
                    VersionCheckDialog(
                        isChecking = isChecking,
                        latestVersion = latestVersion,
                        releaseUrl = releaseUrl,
                        onOpenRelease = onOpenRelease,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }

    fun createCleartextWarningDialogView(
        context: Context,
        url: String,
        onProceed: () -> Unit,
        onDismiss: () -> Unit
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AABrowserTheme {
                    CleartextWarningDialog(
                        url = url,
                        onProceed = onProceed,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }

    fun createConfirmationDialogView(
        context: Context,
        title: String,
        message: String,
        hostLabel: String? = null,
        hostValue: String? = null,
        detailMessage: String? = null,
        isDestructive: Boolean = false,
        confirmButtonText: String? = null,
        onConfirm: (() -> Unit)? = null,
        secondaryButtonText: String? = null,
        onSecondary: (() -> Unit)? = null,
        dismissButtonText: String = context.getString(android.R.string.cancel),
        onDismiss: () -> Unit
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AABrowserTheme {
                    ExpressiveConfirmationContent(
                        title = title,
                        message = message,
                        hostLabel = hostLabel,
                        hostValue = hostValue,
                        detailMessage = detailMessage,
                        isDestructive = isDestructive,
                        confirmButtonText = confirmButtonText,
                        onConfirm = onConfirm,
                        secondaryButtonText = secondaryButtonText,
                        onSecondary = onSecondary,
                        dismissButtonText = dismissButtonText,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}
