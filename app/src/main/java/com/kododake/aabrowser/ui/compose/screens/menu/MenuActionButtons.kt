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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveMotion

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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MenuNavigationButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = stringResource(R.string.menu_back),
            enabled = canGoBack,
            onClick = onBack,
            topStart = 24.dp,
            topEnd = 8.dp,
            bottomEnd = 8.dp,
            bottomStart = 24.dp
        )
        MenuNavigationButton(
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            description = stringResource(R.string.menu_forward),
            enabled = canGoForward,
            onClick = onForward,
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomEnd = 8.dp,
            bottomStart = 8.dp
        )
        MenuNavigationButton(
            icon = Icons.Rounded.Refresh,
            description = stringResource(R.string.menu_reload),
            enabled = canReload,
            onClick = onReload,
            topStart = 8.dp,
            topEnd = 24.dp,
            bottomEnd = 24.dp,
            bottomStart = 8.dp
        )
    }
}

@Composable
private fun RowScope.MenuNavigationButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morphTopStart by animateDpAsState(
        targetValue = if (isPressed && enabled) 20.dp else topStart,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "navTopStart"
    )
    val morphTopEnd by animateDpAsState(
        targetValue = if (isPressed && enabled) 20.dp else topEnd,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "navTopEnd"
    )
    val morphBottomEnd by animateDpAsState(
        targetValue = if (isPressed && enabled) 20.dp else bottomEnd,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "navBottomEnd"
    )
    val morphBottomStart by animateDpAsState(
        targetValue = if (isPressed && enabled) 20.dp else bottomStart,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "navBottomStart"
    )

    val shape = RoundedCornerShape(
        topStart = morphTopStart,
        topEnd = morphTopEnd,
        bottomEnd = morphBottomEnd,
        bottomStart = morphBottomStart
    )

    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
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
            .bouncyClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                shape = shape,
                onClick = onClick
            )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}
