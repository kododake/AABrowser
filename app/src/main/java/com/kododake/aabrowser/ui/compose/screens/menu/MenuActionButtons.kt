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

package com.kododake.aabrowser.ui.compose.screens.menu

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R

private data class NavActionSpec(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenuNavigationRow(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        NavActionSpec(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.menu_back), canGoBack, onBack),
        NavActionSpec(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.menu_forward), canGoForward, onForward),
        NavActionSpec(Icons.Rounded.Refresh, stringResource(R.string.menu_reload), canReload, onReload)
    )

    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth()
    ) {
        actions.forEachIndexed { index, spec ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val groupShapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    val shapes: ButtonShapes = ButtonDefaults.shapes(
                        shape = groupShapes.shape,
                        pressedShape = groupShapes.pressedShape
                    )
                    FilledTonalButton(
                        onClick = spec.onClick,
                        enabled = spec.enabled,
                        shapes = shapes,
                        interactionSource = interactionSource,
                        border = null,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = containerColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = containerColor.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        elevation = ButtonDefaults.filledTonalButtonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .animateWidth(interactionSource)
                    ) {
                        Icon(spec.icon, contentDescription = spec.description, modifier = Modifier.size(24.dp))
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(spec.description) },
                        leadingIcon = { Icon(spec.icon, contentDescription = null) },
                        enabled = spec.enabled,
                        onClick = {
                            state.dismiss()
                            spec.onClick()
                        }
                    )
                }
            )
        }
    }
}
