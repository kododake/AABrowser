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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SliderState
import com.kododake.aabrowser.ui.compose.theme.ExpressiveMotion

/**
 * Material 3 Expressive thick track (16dp) slider with elastic vertical handle (4x44dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0.8f..1.5f,
    steps: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    val handleWidth by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = ExpressiveMotion.DpBouncySpring,
        label = "handleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = if (isDragging) 48.dp else 44.dp,
        animationSpec = ExpressiveMotion.DpBouncySpring,
        label = "handleHeight"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

    val sliderState = remember(steps, valueRange) {
        SliderState(
            value = value,
            steps = steps,
            trackRange = valueRange
        )
    }
    sliderState.value = value

    Slider(
        state = sliderState,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = handleWidth, height = handleHeight)
                    .background(primaryColor, RoundedCornerShape(2.dp))
            )
        },
        track = {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                val fraction = if (valueRange.endInclusive > valueRange.start) {
                    ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f

                val corner = CornerRadius(8.dp.toPx())
                drawRoundRect(
                    color = secondaryContainerColor,
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = primaryColor,
                    size = size.copy(width = size.width * fraction),
                    cornerRadius = corner
                )
            }
        }
    )
}
