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

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.ui.compose.components.ExpressiveBottomSheetContainer

@Composable
fun QrCodeShareSheet(
    isVisible: Boolean,
    url: String,
    qrBitmap: Bitmap?,
    onCopyUrl: () -> Unit,
    onShareExternal: () -> Unit,
    onClose: () -> Unit,
    onDismissFinished: () -> Unit,
    onDismiss: () -> Unit = onClose,
    animateEnter: Boolean = true,
    onProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ExpressiveBottomSheetContainer(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        animateEnter = animateEnter,
        onProgress = onProgress,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrSheetHeader(onClose = onClose)

            Spacer(Modifier.height(16.dp))

            QrDisplayCard(qrBitmap = qrBitmap)

            Spacer(Modifier.height(16.dp))

            QrUrlCard(
                url = url,
                onCopyUrl = onCopyUrl,
                onShareExternal = onShareExternal
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
