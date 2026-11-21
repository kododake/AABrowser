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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kododake.aabrowser.R

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val defaultShape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )

    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentAlpha = if (enabled) 1f else 0.38f

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(
            shape = defaultShape,
            pressedShape = RoundedCornerShape(20.dp)
        ),
        border = null,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = containerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
            .weight(1f)
            .height(72.dp)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.5.sp,
                    maxFontSize = 12.sp,
                    stepSize = 0.5.sp
                )
            )
        }
    }
}
