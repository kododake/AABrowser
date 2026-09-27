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
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.ui.compose.components.ExpressiveListItem
import com.kododake.aabrowser.ui.compose.components.ListGroupPosition
import com.kododake.aabrowser.web.adblock.AdBlocker
import com.kododake.aabrowser.web.adblock.RemoteFilterListManager

/**
 * Shields (ad & tracker blocking) settings: the global switch plus filter-list management
 * (built-in subscriptions, custom lists and a manual update).
 */
@Composable
fun ShieldsSettingsComposable(
    context: Context,
    onShieldsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shieldsEnabled by remember { mutableStateOf(BrowserPreferences.isShieldsEnabled(context)) }
    var subscriptions by remember { mutableStateOf(RemoteFilterListManager.subscriptions(context)) }
    var isUpdating by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    fun reloadSubscriptions() {
        subscriptions = RemoteFilterListManager.subscriptions(context)
    }

    fun refreshLists() {
        if (isUpdating) return
        isUpdating = true
        Toast.makeText(context, R.string.settings_filter_lists_updating, Toast.LENGTH_SHORT).show()
        RemoteFilterListManager.refresh(context, force = true) { result ->
            isUpdating = false
            reloadSubscriptions()
            Toast.makeText(
                context,
                context.getString(R.string.settings_filter_lists_update_result, result.updated, result.failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setShields(enabled: Boolean) {
        shieldsEnabled = enabled
        BrowserPreferences.setShieldsEnabled(context, enabled)
        onShieldsChanged()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_shields),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.settings_shields_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
        )

        val statusText = if (shieldsEnabled) {
            stringResource(R.string.settings_shields_blocked_count, AdBlocker.blockedThisSession.toInt())
        } else {
            stringResource(R.string.settings_shields_disabled)
        }
        ExpressiveListItem(
            headline = stringResource(R.string.settings_shields_title),
            supportingText = statusText,
            leadingIcon = Icons.Rounded.Shield,
            position = ListGroupPosition.Top,
            trailingContent = {
                Switch(
                    checked = shieldsEnabled,
                    onCheckedChange = { checked -> setShields(checked) }
                )
            },
            onClick = { setShields(!shieldsEnabled) }
        )

        Spacer(Modifier.height(3.dp))

        val enabledCount = subscriptions.count { it.enabled }
        val ruleCount = subscriptions.filter { it.enabled }.sumOf { it.ruleCount }
        ExpressiveListItem(
            headline = stringResource(R.string.settings_filter_lists_manage),
            supportingText = stringResource(
                R.string.settings_filter_lists_summary, enabledCount, subscriptions.size, ruleCount
            ),
            leadingIcon = Icons.Rounded.FilterList,
            position = ListGroupPosition.Middle,
            onClick = {
                reloadSubscriptions()
                showManageDialog = true
            }
        )

        Spacer(Modifier.height(3.dp))

        ExpressiveListItem(
            headline = stringResource(R.string.settings_filter_lists_add),
            supportingText = stringResource(R.string.settings_filter_list_url),
            leadingIcon = Icons.Rounded.Add,
            position = ListGroupPosition.Middle,
            onClick = { showAddDialog = true }
        )

        Spacer(Modifier.height(3.dp))

        ExpressiveListItem(
            headline = stringResource(R.string.settings_filter_lists_update),
            supportingText = if (isUpdating) {
                stringResource(R.string.settings_filter_lists_updating)
            } else {
                stringResource(R.string.settings_filter_lists_manage_description)
            },
            leadingIcon = Icons.Rounded.Refresh,
            position = ListGroupPosition.Bottom,
            onClick = { refreshLists() }
        )
    }

    if (showManageDialog) {
        val lists = subscriptions
        val checked = remember(lists) { mutableStateOf(lists.map { it.enabled }) }
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.settings_filter_lists_manage)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    lists.forEachIndexed { index, item ->
                        val suffix = when {
                            item.lastError != null -> stringResource(R.string.settings_filter_list_failed)
                            item.ruleCount > 0 -> stringResource(R.string.settings_filter_list_rules, item.ruleCount)
                            else -> stringResource(R.string.settings_filter_list_not_downloaded)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked.value[index],
                                onCheckedChange = { value ->
                                    checked.value = checked.value.toMutableList().also { it[index] = value }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${item.title} · $suffix",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val enabledIds = lists.indices.filter { checked.value[it] }.mapTo(HashSet()) { lists[it].id }
                    showManageDialog = false
                    RemoteFilterListManager.setEnabledAsync(context, enabledIds) { reloadSubscriptions() }
                }) {
                    Text(stringResource(R.string.settings_action_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showManageDialog = false
                        if (lists.none { !it.builtIn }) {
                            Toast.makeText(context, R.string.settings_filter_lists_no_custom, Toast.LENGTH_SHORT).show()
                        } else {
                            showRemoveDialog = true
                        }
                    }) {
                        Text(stringResource(R.string.settings_filter_lists_remove))
                    }
                    TextButton(onClick = { showManageDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    if (showRemoveDialog) {
        val custom = subscriptions.filterNot { it.builtIn }
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.settings_filter_lists_remove)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    custom.forEach { item ->
                        TextButton(
                            onClick = {
                                showRemoveDialog = false
                                RemoteFilterListManager.removeCustomAsync(context, item.id) { reloadSubscriptions() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(item.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var urlError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.settings_filter_lists_add)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.settings_filter_list_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            urlError = null
                        },
                        label = { Text(stringResource(R.string.settings_filter_list_url)) },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        isError = urlError != null,
                        supportingText = if (urlError != null) {
                            { Text(urlError.orEmpty()) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { RemoteFilterListManager.addCustom(context, name, url) }
                    if (result.isFailure) {
                        urlError = result.exceptionOrNull()?.message
                            ?: context.getString(R.string.settings_filter_list_invalid)
                    } else {
                        showAddDialog = false
                        reloadSubscriptions()
                        refreshLists()
                    }
                }) {
                    Text(stringResource(R.string.settings_action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
