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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

data class ConnectedButtonSpec(
    val label: String = "",
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val weight: Float = 1f,
    val colors: ButtonColors? = null
)

/**
 * Material 3 Expressive Connected Button Group.
 * Outer edges 28dp pill, inner adjacent edges 8dp, 3dp gap between buttons.
 */
@Composable
fun ConnectedButtonGroup(
    buttons: List<ConnectedButtonSpec>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEachIndexed { index, spec ->
            val shape = when {
                buttons.size == 1 -> CircleShape
                index == 0 -> RoundedCornerShape(
                    topStart = 28.dp,
                    bottomStart = 28.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp
                )
                index == buttons.lastIndex -> RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp
                )
                else -> RoundedCornerShape(8.dp)
            }

            Button(
                onClick = spec.onClick,
                modifier = Modifier
                    .weight(spec.weight)
                    .fillMaxHeight()
                    .bouncyClickable(onClick = spec.onClick),
                shape = shape,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
                colors = spec.colors ?: ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (spec.icon != null) {
                    Icon(spec.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    if (spec.label.isNotBlank()) Spacer(Modifier.width(8.dp))
                }
                if (spec.label.isNotBlank()) {
                    Text(spec.label, style = ExpressiveTypography.labelLargeEmphasized)
                }
            }
        }
    }
}
