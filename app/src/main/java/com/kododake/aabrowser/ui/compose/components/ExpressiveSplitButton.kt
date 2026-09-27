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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.ui.compose.theme.ExpressiveMotion
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

data class SplitMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Material 3 Expressive SplitButton with spring arrow rotation and corner morphing.
 */
@Composable
fun ExpressiveSplitButton(
    label: String,
    leadingIcon: ImageVector,
    menuItems: List<SplitMenuItem>,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    var expanded by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ExpressiveMotion.BouncySpring,
        label = "arrowRotation"
    )

    val innerCornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 8.dp,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "cornerMorph"
    )

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .bouncyClickable(onClick = onPrimaryClick),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                bottomStart = 28.dp,
                topEnd = innerCornerRadius,
                bottomEnd = innerCornerRadius
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = ExpressiveTypography.labelLargeEmphasized)
        }

        Box(modifier = Modifier.fillMaxHeight()) {
            Button(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .bouncyClickable { expanded = !expanded },
                shape = RoundedCornerShape(
                    topStart = innerCornerRadius,
                    bottomStart = innerCornerRadius,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = "Options",
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title, style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            expanded = false
                            item.onClick()
                        }
                    )
                }
            }
        }
    }
}
