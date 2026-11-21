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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.ui.compose.components.ExpressiveListItem
import com.kododake.aabrowser.ui.compose.screens.dialogs.ExpressiveSingleChoiceDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)

@Composable
fun InAppControlsComposable(
    context: Context,
    onInAppControlsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fabAlwaysVisible by remember { mutableStateOf(BrowserPreferences.isQuickActionButtonAlwaysVisible(context)) }
    var fabPos by remember { mutableStateOf(BrowserPreferences.getQuickActionButtonPosition(context)) }

    var showFabPosDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_in_app_controls),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            ExpressiveListItem(
                headline = stringResource(R.string.settings_quick_action_button_always_visible),
                supportingText = stringResource(R.string.settings_quick_action_button_always_visible_description),
                leadingIcon = Icons.Rounded.TouchApp,
                index = 0,
                count = 2,
                trailingContent = {
                    Switch(
                        checked = fabAlwaysVisible,
                        onCheckedChange = { checked ->
                            fabAlwaysVisible = checked
                            BrowserPreferences.setQuickActionButtonAlwaysVisible(context, checked)
                            onInAppControlsChanged()
                        }
                    )
                },
                onClick = {
                    val next = !fabAlwaysVisible
                    fabAlwaysVisible = next
                    BrowserPreferences.setQuickActionButtonAlwaysVisible(context, next)
                    onInAppControlsChanged()
                }
            )

            val posLabel = when (fabPos) {
                QuickActionButtonPosition.BOTTOM_LEFT -> stringResource(R.string.settings_quick_action_button_position_bottom_left)
                QuickActionButtonPosition.BOTTOM_RIGHT -> stringResource(R.string.settings_quick_action_button_position_bottom_right)
                QuickActionButtonPosition.TOP_LEFT -> stringResource(R.string.settings_quick_action_button_position_top_left)
                QuickActionButtonPosition.TOP_RIGHT -> stringResource(R.string.settings_quick_action_button_position_top_right)
            }
            ExpressiveListItem(
                headline = stringResource(R.string.settings_quick_action_button_position),
                supportingText = posLabel,
                leadingIcon = Icons.Rounded.Place,
                index = 1,
                count = 2,
                onClick = { showFabPosDialog = true }
            )
        }
    }

    if (showFabPosDialog) {
        ExpressiveSingleChoiceDialog(
            title = stringResource(R.string.settings_quick_action_button_position),
            items = QuickActionButtonPosition.entries,
            selectedItem = fabPos,
            onItemSelected = { pos ->
                fabPos = pos
                BrowserPreferences.setQuickActionButtonPosition(context, pos)
                onInAppControlsChanged()
            },
            onDismiss = { showFabPosDialog = false },
            itemLabel = { pos ->
                when (pos) {
                    QuickActionButtonPosition.BOTTOM_LEFT -> stringResource(R.string.settings_quick_action_button_position_bottom_left)
                    QuickActionButtonPosition.BOTTOM_RIGHT -> stringResource(R.string.settings_quick_action_button_position_bottom_right)
                    QuickActionButtonPosition.TOP_LEFT -> stringResource(R.string.settings_quick_action_button_position_top_left)
                    QuickActionButtonPosition.TOP_RIGHT -> stringResource(R.string.settings_quick_action_button_position_top_right)
                }
            }
        )
    }
}
