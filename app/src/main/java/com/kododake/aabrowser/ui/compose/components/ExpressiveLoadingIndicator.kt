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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Morphing Loading Indicator.
 * Smoothly morphs between Circle, Flower, Star, and Squircle shapes with spring-like physics.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    withContainer: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "morphIndicator")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "rotation"
    )

    val morphPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "morphPhase"
    )

    val scalePulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    val path = remember { Path() }

    val indicatorContent = @Composable {
        Canvas(
            modifier = Modifier
                .size(size)
                .rotate(rotation)
                .graphicsLayer {
                    scaleX = scalePulse
                    scaleY = scalePulse
                }
        ) {
            val center = this.center
            val radius = this.size.minDimension / 2f
            path.reset()

            val points = 72
            val step = (2.0 * Math.PI / points).toFloat()

            val currentPhaseInt = morphPhase.toInt().coerceIn(0, 3)
            val phaseFraction = morphPhase - morphPhase.toInt()
            val t = FastOutSlowInEasing.transform(phaseFraction)

            for (i in 0 until points) {
                val angle = i * step

                val r0 = 1.0f
                val r1 = 0.70f + 0.30f * cos(4f * angle)
                val r2 = 0.60f + 0.40f * cos(8f * angle)
                val r3 = 0.85f + 0.15f * cos(4f * angle)

                val normalizedRadius = when (currentPhaseInt) {
                    0 -> r0 + t * (r1 - r0)
                    1 -> r1 + t * (r2 - r1)
                    2 -> r2 + t * (r3 - r2)
                    else -> r3 + t * (r0 - r3)
                }

                val r = radius * normalizedRadius
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(path = path, color = color)
        }
    }

    if (withContainer) {
        val isDark = isSystemInDarkTheme()
        val containerColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
        Box(
            modifier = modifier
                .size(size + 24.dp)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            indicatorContent()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            indicatorContent()
        }
    }
}
