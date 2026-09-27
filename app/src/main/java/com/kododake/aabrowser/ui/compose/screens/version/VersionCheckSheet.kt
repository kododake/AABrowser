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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.ExpressiveBottomSheetContainer
import com.kododake.aabrowser.ui.compose.components.bouncyClickable

@Composable
fun VersionCheckSheet(
    isVisible: Boolean,
    isChecking: Boolean,
    latestVersion: String?,
    installedVersion: String,
    releaseUrl: String?,
    onOpenRelease: (String) -> Unit,
    onClose: () -> Unit,
    onDismissFinished: () -> Unit,
    onDismiss: () -> Unit = onClose,
    animateEnter: Boolean = true,
    onProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUpToDate = latestVersion != null && latestVersion.trim().removePrefix("v").equals(
        installedVersion.trim().removePrefix("v"),
        ignoreCase = true
    )

    ExpressiveBottomSheetContainer(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        animateEnter = animateEnter,
        onProgress = onProgress,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VersionSheetHeader(onClose = onClose)

            Spacer(Modifier.height(16.dp))

            VersionStatusCard(
                isChecking = isChecking,
                isUpToDate = isUpToDate,
                installedVersion = installedVersion,
                latestVersion = latestVersion
            )

            Spacer(Modifier.height(20.dp))

            if (!isChecking && !isUpToDate && !releaseUrl.isNullOrBlank()) {
                Button(
                    onClick = { onOpenRelease(releaseUrl) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .bouncyClickable { onOpenRelease(releaseUrl) }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.check_latest_view_release))
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
