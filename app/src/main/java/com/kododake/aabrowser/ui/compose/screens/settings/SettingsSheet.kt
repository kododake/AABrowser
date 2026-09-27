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

package com.kododake.aabrowser.ui.compose.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.ui.compose.components.ExpressiveBottomSheetContainer

@Composable
fun SettingsSheet(
    isVisible: Boolean,
    context: Context,
    callbacks: SettingsCallbacks,
    onClose: () -> Unit,
    onDismissFinished: () -> Unit,
    onDismiss: () -> Unit = callbacks.onDismiss,
    animateEnter: Boolean = true,
    onProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sessionKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(isVisible) {
        if (isVisible) sessionKey++
    }

    ExpressiveBottomSheetContainer(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        animateEnter = animateEnter,
        onProgress = onProgress,
        sheetModifier = Modifier.fillMaxHeight(0.92f),
        modifier = modifier
    ) {
        SettingsScreen(
            context = context,
            includeDragHandle = false,
            isScrollable = true,
            applyBackground = false,
            applyTheme = false,
            sessionKey = sessionKey,
            callbacks = callbacks.copy(onClose = onClose, onDismiss = onDismiss),
            modifier = Modifier.fillMaxSize()
        )
    }
}
