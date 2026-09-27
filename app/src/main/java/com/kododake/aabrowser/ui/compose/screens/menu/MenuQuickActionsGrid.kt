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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveMotion

@Composable
fun MenuQuickActionsGrid(
    onBookmarks: () -> Unit,
    onQrCode: () -> Unit,
    canQrCode: Boolean = false,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            M3EGridButton(
                icon = Icons.Rounded.Bookmark,
                label = stringResource(R.string.menu_bookmarks),
                onClick = onBookmarks,
                topStart = 24.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
                bottomStart = 6.dp
            )
            M3EGridButton(
                icon = Icons.Rounded.QrCode2,
                label = stringResource(R.string.menu_share),
                onClick = onQrCode,
                enabled = canQrCode,
                topStart = 6.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
                bottomStart = 6.dp
            )
            M3EGridButton(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.menu_settings),
                onClick = onSettings,
                topStart = 6.dp,
                topEnd = 24.dp,
                bottomEnd = 6.dp,
                bottomStart = 6.dp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            M3EGridButton(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.menu_start_page),
                onClick = onHome,
                topStart = 6.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
                bottomStart = 24.dp
            )
            M3EGridButton(
                icon = Icons.Rounded.Tab,
                label = stringResource(R.string.menu_tabs),
                onClick = onTabs,
                topStart = 6.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
                bottomStart = 6.dp
            )
            M3EGridButton(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.menu_new_tab),
                onClick = onNewTab,
                topStart = 6.dp,
                topEnd = 6.dp,
                bottomEnd = 24.dp,
                bottomStart = 6.dp
            )
        }
    }
}

@Composable
private fun RowScope.M3EGridButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morphTopStart by animateDpAsState(
        targetValue = if (isPressed) 20.dp else topStart,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "gridTopStart"
    )
    val morphTopEnd by animateDpAsState(
        targetValue = if (isPressed) 20.dp else topEnd,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "gridTopEnd"
    )
    val morphBottomEnd by animateDpAsState(
        targetValue = if (isPressed) 20.dp else bottomEnd,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "gridBottomEnd"
    )
    val morphBottomStart by animateDpAsState(
        targetValue = if (isPressed) 20.dp else bottomStart,
        animationSpec = ExpressiveMotion.CornerMorphSpring,
        label = "gridBottomStart"
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
    val contentAlpha = if (enabled) 1f else 0.38f

    FilledTonalButton(
        onClick = { if (enabled) onClick() },
        shape = shape,
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.25f else 0.12f)
        ),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
            .then(
                if (enabled) Modifier.bouncyClickable(
                    interactionSource = interactionSource,
                    shape = shape,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        }
    }
}
