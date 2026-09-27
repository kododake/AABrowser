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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kododake.aabrowser.R

@Composable
fun MenuDesktopSwitchCard(
    isDesktopMode: Boolean,
    onDesktopToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    MenuSwitchCard(
        title = stringResource(R.string.menu_desktop_mode),
        icon = Icons.Rounded.Computer,
        isChecked = isDesktopMode,
        onCheckedChange = onDesktopToggle,
        modifier = modifier
    )
}
