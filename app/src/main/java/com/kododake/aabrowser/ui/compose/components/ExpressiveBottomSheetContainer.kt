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

package com.kododake.aabrowser.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalSheetDragModifier = compositionLocalOf<Modifier> { Modifier }

/**
 * Unified Material 3 Expressive bottom sheet container.
 * Encapsulates the drag handle, vertical drag gestures, tinted shadows,
 * frosted glass border, dynamic gradient background, and dismiss animations.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBottomSheetContainer(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetModifier: Modifier = Modifier,
    animateEnter: Boolean = true,
    isReturning: Boolean = false,
    keepScrimOnClose: Boolean = false,
    onProgress: (Float) -> Unit = {},
    onDismissFinished: () -> Unit = {},
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AABrowserTheme {
        val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

        val scope = rememberCoroutineScope()
        var sheetHeight by remember { mutableIntStateOf(1000) }
        val sheetOffsetY = remember { Animatable(if (animateEnter && !isReturning) 2000f else 0f) }
        val scrimAlpha = remember { Animatable(if (animateEnter && !isReturning) 0f else 0.45f) }
        var hasOpened by remember { mutableStateOf(!animateEnter || isReturning) }

        LaunchedEffect(isVisible, animateEnter, isReturning) {
            if (isVisible) {
                hasOpened = true
                if (isReturning) {
                    scrimAlpha.snapTo(0.45f)
                    sheetOffsetY.snapTo(if (sheetHeight > 0) sheetHeight.toFloat() else 2000f)
                    sheetOffsetY.animateTo(0f, defaultSpatialSpec)
                } else if (!animateEnter) {
                    scrimAlpha.snapTo(0.45f)
                    sheetOffsetY.snapTo(0f)
                } else {
                    scrimAlpha.snapTo(0f)
                    sheetOffsetY.snapTo(if (sheetHeight > 0) sheetHeight.toFloat() else 2000f)
                    launch { scrimAlpha.animateTo(0.45f, defaultEffectsSpec) }
                    sheetOffsetY.animateTo(0f, defaultSpatialSpec)
                }
            } else if (hasOpened) {
                if (!keepScrimOnClose) {
                    launch { scrimAlpha.animateTo(0f, tween(250, easing = FastOutLinearInEasing)) }
                }
                sheetOffsetY.animateTo(
                    targetValue = sheetHeight.toFloat(),
                    animationSpec = tween(250, easing = FastOutLinearInEasing)
                )
                onDismissFinished()
            }
        }

        LaunchedEffect(sheetOffsetY.value, sheetHeight, keepScrimOnClose, hasOpened) {
            if (!hasOpened) return@LaunchedEffect
            if (keepScrimOnClose) {
                onProgress(1f)
            } else if (sheetHeight > 0) {
                val progress = (1f - (sheetOffsetY.value / sheetHeight.toFloat())).coerceIn(0f, 1f)
                onProgress(progress)
            }
        }

        val isDark = isSystemInDarkTheme()
        val surfaceBase = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

        val bgGradient = remember(isDark, surfaceBase, surfaceHigh, primaryContainer, secondaryContainer) {
            if (isDark) {
                Brush.verticalGradient(listOf(surfaceBase, surfaceHigh))
            } else {
                Brush.verticalGradient(
                    listOf(
                        primaryContainer.copy(alpha = 0.20f),
                        secondaryContainer.copy(alpha = 0.12f),
                        surfaceBase
                    )
                )
            }
        }

        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dragFraction = (sheetOffsetY.value / sheetHeight.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        alpha = (scrimAlpha.value * (1f - dragFraction)).coerceIn(0f, 1f)
                    }
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isVisible,
                        onClick = onDismissRequest
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(sheetModifier)
                    .offset { IntOffset(0, sheetOffsetY.value.roundToInt()) }
                    .onSizeChanged { if (it.height > 0) sheetHeight = it.height }
                    .shadow(
                        elevation = 16.dp,
                        shape = sheetShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
                    )
                    .background(surfaceBase, sheetShape)
                    .background(bgGradient, sheetShape)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                        sheetShape
                    )
                    .clip(sheetShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                val dragModifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                sheetOffsetY.snapTo((sheetOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            if (sheetOffsetY.value > 140f) {
                                onDismissRequest()
                            } else {
                                scope.launch { sheetOffsetY.animateTo(0f, fastSpatialSpec) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { sheetOffsetY.animateTo(0f, fastSpatialSpec) }
                        }
                    )
                }

                CompositionLocalProvider(LocalSheetDragModifier provides dragModifier) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showDragHandle) {
                            ExpressiveDragHandle(modifier = dragModifier)
                        }

                        content()
                    }
                }
            }
        }
    }
}
