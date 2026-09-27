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

package com.kododake.aabrowser.ui.compose.screens.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.BuildConfig
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.ExpressiveLoadingIndicator
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

@Composable
fun VersionCheckDialog(
    isChecking: Boolean,
    latestVersion: String?,
    releaseUrl: String?,
    onOpenRelease: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentVersion = "v${BuildConfig.VERSION_NAME}"
    val isUpToDate = latestVersion != null && latestVersion.trim().removePrefix("v").equals(
        BuildConfig.VERSION_NAME.trim().removePrefix("v"),
        ignoreCase = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.menu_check_latest),
                    style = ExpressiveTypography.titleLargeEmphasized
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isChecking) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator()
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.menu_checking_latest),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = if (isUpToDate) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = if (isUpToDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.installed_version_label, currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = if (isUpToDate) {
                            stringResource(R.string.check_latest_up_to_date, latestVersion)
                        } else {
                            stringResource(R.string.check_latest_update_available, latestVersion.orEmpty())
                        },
                        style = ExpressiveTypography.labelLargeEmphasized,
                        color = if (isUpToDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (!isChecking && !isUpToDate && !releaseUrl.isNullOrBlank()) {
                Button(
                    onClick = { onOpenRelease(releaseUrl) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.bouncyClickable { onOpenRelease(releaseUrl) }
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.check_latest_view_release))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bouncyClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.menu_close))
            }
        }
    )
}
