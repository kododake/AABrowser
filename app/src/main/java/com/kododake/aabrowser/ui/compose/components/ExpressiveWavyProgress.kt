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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Linear Progress Indicator conforming to M3 guidelines.
 * Clean, straight-line indicator with dynamic device theme colors and zero gaps.
 */
@Composable
fun ExpressiveWavyProgress(
    progress: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    thickness: Dp = 3.5.dp
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "linearProgress"
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness)
        ) {
            val width = size.width
            val strokeWidthPx = thickness.toPx()
            val lineY = strokeWidthPx / 2f

            drawLine(
                color = trackColor,
                start = Offset(0f, lineY),
                end = Offset(width, lineY),
                strokeWidth = strokeWidthPx
            )

            val endX = width * animatedProgress
            if (endX > 0f) {
                drawLine(
                    color = primaryColor,
                    start = Offset(0f, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = strokeWidthPx,
                    cap = if (animatedProgress < 1f) StrokeCap.Round else StrokeCap.Butt
                )
            }
        }
    }
}
