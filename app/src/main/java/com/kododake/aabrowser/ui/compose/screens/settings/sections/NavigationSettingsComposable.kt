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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun NavigationSettingsComposable(
    context: Context,
    onHomePageChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var homePageUrl by remember { mutableStateOf(BrowserPreferences.getHomePageUrl(context).orEmpty()) }
    var restoreTabs by remember { mutableStateOf(BrowserPreferences.shouldRestoreTabsOnLaunch(context)) }
    var resumeLastPage by remember { mutableStateOf(BrowserPreferences.shouldResumeLastPageOnLaunch(context)) }
    var showHomePageDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_startup),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        val homeSub = if (homePageUrl.isBlank()) {
            stringResource(R.string.settings_home_page_inactive)
        } else {
            homePageUrl
        }
        ExpressiveListItem(
            headline = stringResource(R.string.settings_home_page),
            supportingText = homeSub,
            leadingIcon = Icons.Rounded.Home,
            position = ListGroupPosition.Top,
            onClick = { showHomePageDialog = true }
        )

        Spacer(Modifier.height(3.dp))

        ExpressiveListItem(
            headline = stringResource(R.string.settings_restore_tabs_on_launch),
            supportingText = stringResource(R.string.settings_restore_tabs_on_launch_description),
            leadingIcon = Icons.Rounded.Tab,
            position = ListGroupPosition.Middle,
            trailingContent = {
                Switch(
                    checked = restoreTabs,
                    onCheckedChange = { checked ->
                        restoreTabs = checked
                        BrowserPreferences.setRestoreTabsOnLaunch(context, checked)
                    }
                )
            },
            onClick = {
                val next = !restoreTabs
                restoreTabs = next
                BrowserPreferences.setRestoreTabsOnLaunch(context, next)
            }
        )

        Spacer(Modifier.height(3.dp))

        ExpressiveListItem(
            headline = stringResource(R.string.settings_resume_last_page_on_launch),
            supportingText = stringResource(R.string.settings_resume_last_page_on_launch_description),
            leadingIcon = Icons.Rounded.History,
            position = ListGroupPosition.Bottom,
            trailingContent = {
                Switch(
                    checked = resumeLastPage,
                    onCheckedChange = { checked ->
                        resumeLastPage = checked
                        BrowserPreferences.setResumeLastPageOnLaunch(context, checked)
                    }
                )
            },
            onClick = {
                val next = !resumeLastPage
                resumeLastPage = next
                BrowserPreferences.setResumeLastPageOnLaunch(context, next)
            }
        )
    }

    if (showHomePageDialog) {
        var tempUrl by remember { mutableStateOf(homePageUrl) }
        AlertDialog(
            onDismissRequest = { showHomePageDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.settings_home_page)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    homePageUrl = tempUrl.trim()
                    BrowserPreferences.setHomePageUrl(context, homePageUrl.ifBlank { null })
                    showHomePageDialog = false
                    onHomePageChanged()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    homePageUrl = ""
                    BrowserPreferences.setHomePageUrl(context, null)
                    showHomePageDialog = false
                    onHomePageChanged()
                }) {
                    Text(stringResource(R.string.settings_home_page_clear))
                }
            }
        )
    }
}
