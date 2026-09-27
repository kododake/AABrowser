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

package com.kododake.aabrowser.ui.compose.screens.settings.sections

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.ui.compose.components.ExpressiveListItem
import com.kododake.aabrowser.ui.compose.components.ListGroupPosition
import com.kododake.aabrowser.ui.compose.screens.dialogs.ExpressiveSingleChoiceDialog

@Composable
fun AppearanceSettingsComposable(
    context: Context,
    onThemeChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentThemeMode by remember { mutableStateOf(BrowserPreferences.getThemeMode(context)) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        val themeSubtitle = when (currentThemeMode) {
            AppThemeMode.AUTO -> stringResource(R.string.settings_theme_auto)
            AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
            AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        }
        ExpressiveListItem(
            headline = stringResource(R.string.settings_appearance),
            supportingText = themeSubtitle,
            leadingIcon = Icons.Rounded.Palette,
            position = ListGroupPosition.Single,
            onClick = { showThemeDialog = true }
        )
    }

    if (showThemeDialog) {
        ExpressiveSingleChoiceDialog(
            title = stringResource(R.string.settings_appearance),
            items = AppThemeMode.entries,
            selectedItem = currentThemeMode,
            onItemSelected = { mode ->
                currentThemeMode = mode
                BrowserPreferences.setThemeMode(context, mode)
                onThemeChanged()
            },
            onDismiss = { showThemeDialog = false },
            itemLabel = { mode ->
                when (mode) {
                    AppThemeMode.AUTO -> stringResource(R.string.settings_theme_auto)
                    AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                }
            }
        )
    }
}
