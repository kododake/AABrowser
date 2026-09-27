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

package com.kododake.aabrowser.ui.compose.screens.share

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object QrViews {

    fun setup(
        composeView: ComposeView,
        isVisibleState: State<Boolean>,
        urlState: State<String>,
        qrBitmapState: State<Bitmap?>,
        animateEnterProvider: () -> Boolean = { true },
        onCopyUrl: () -> Unit,
        onShareExternal: () -> Unit,
        onClose: () -> Unit,
        onDismissFinished: () -> Unit,
        onDismiss: () -> Unit = onClose,
        onProgress: (Float) -> Unit = {}
    ) {
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QrCodeShareSheet(
                    isVisible = isVisibleState.value,
                    url = urlState.value,
                    qrBitmap = qrBitmapState.value,
                    onCopyUrl = onCopyUrl,
                    onShareExternal = onShareExternal,
                    onClose = onClose,
                    onDismissFinished = onDismissFinished,
                    onDismiss = onDismiss,
                    animateEnter = animateEnterProvider(),
                    onProgress = onProgress
                )
            }
        }
    }

    fun createQrCodeSheetContent(
        context: Context,
        isVisibleState: State<Boolean>,
        urlState: State<String>,
        qrBitmapState: State<Bitmap?>,
        onCopyUrl: () -> Unit,
        onShareExternal: () -> Unit,
        onClose: () -> Unit,
        onDismissFinished: () -> Unit,
        onDismiss: () -> Unit = onClose,
        onProgress: (Float) -> Unit = {}
    ): View {
        return ComposeView(context).also { view ->
            setup(
                composeView = view,
                isVisibleState = isVisibleState,
                urlState = urlState,
                qrBitmapState = qrBitmapState,
                onCopyUrl = onCopyUrl,
                onShareExternal = onShareExternal,
                onClose = onClose,
                onDismissFinished = onDismissFinished,
                onDismiss = onDismiss,
                onProgress = onProgress
            )
        }
    }
}

