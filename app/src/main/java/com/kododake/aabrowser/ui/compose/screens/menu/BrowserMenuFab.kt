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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme

/**
 * Compose-driven floating action button for the browser menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserMenuFab(
    stateHolder: MenuStateHolder,
    onClick: () -> Unit
) {
    AABrowserTheme {
        AnimatedVisibility(
            visible = stateHolder.isFabVisible,
            enter = scaleIn(tween(180)) + fadeIn(tween(180)),
            exit = scaleOut(tween(120)) + fadeOut(tween(120)),
            modifier = Modifier.padding(6.dp)
        ) {
            val fabShape = RoundedCornerShape(18.dp)
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.size(56.dp),
                shape = fabShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp,
                    focusedElevation = 4.dp,
                    hoveredElevation = 4.dp
                )
            ) {
                val iconRes = if (stateHolder.fabIsAddressBarMode) {
                    R.drawable.search_24px
                } else {
                    R.drawable.ic_menu_24px
                }
                val descRes = if (stateHolder.fabIsAddressBarMode) {
                    R.string.menu_open_address_bar
                } else {
                    R.string.menu_open_description
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(descRes),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
