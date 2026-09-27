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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kododake.aabrowser.ui.compose.components.ExpressiveListItem
import com.kododake.aabrowser.ui.compose.components.ListGroupPosition

@Composable
fun StartPageSettingsComposable(
    context: Context,
    onPickStartPageBackground: (() -> Unit)?,
    onClearStartPageBackground: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var bgUri by remember { mutableStateOf(BrowserPreferences.getStartPageBackgroundUri(context)) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_start_page),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        val hasCustomBg = !bgUri.isNullOrBlank()
        ExpressiveListItem(
            headline = stringResource(R.string.settings_start_page_choose_background),
            supportingText = if (hasCustomBg) {
                stringResource(R.string.settings_start_page_background_custom)
            } else {
                stringResource(R.string.settings_start_page_background_default)
            },
            leadingIcon = Icons.Rounded.Image,
            position = ListGroupPosition.Single,
            trailingContent = if (hasCustomBg && onClearStartPageBackground != null) {
                {
                    IconButton(onClick = {
                        onClearStartPageBackground()
                        bgUri = null
                    }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.settings_start_page_clear_background)
                        )
                    }
                }
            } else null,
            onClick = { onPickStartPageBackground?.invoke() }
        )

        Spacer(Modifier.height(3.dp))
    }

}
