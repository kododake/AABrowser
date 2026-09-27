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

package com.kododake.aabrowser.ui.compose.screens.version

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.ExpressiveSheetHeader

@Composable
fun VersionSheetHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveSheetHeader(
        title = stringResource(R.string.check_latest_title),
        subtitle = stringResource(R.string.check_latest_subtitle),
        onBack = onClose,
        icon = Icons.Rounded.SystemUpdate,
        modifier = modifier,
        actions = null
    )
}
