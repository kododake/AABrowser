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

package com.kododake.aabrowser.ui.compose.screens.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.settings.SettingsActivity
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.ui.compose.components.ExpressiveDragHandle
import com.kododake.aabrowser.ui.compose.components.ExpressiveSheetHeader
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.AppearanceSettingsComposable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.DisplayScaleSettingsComposable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.InAppControlsComposable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.NavigationSettingsComposable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.PrivacyAndAboutComposable
import com.kododake.aabrowser.ui.compose.screens.settings.sections.StartPageSettingsComposable
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme

@Composable
fun SettingsScreen(
    context: Context,
    includeDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    isScrollable: Boolean = false,
    applyBackground: Boolean = true,
    applyTheme: Boolean = true,
    sessionKey: Int = 0,
    callbacks: SettingsCallbacks = SettingsCallbacks(),
    modifier: Modifier = Modifier
) {
    @Composable
    fun ScreenBody() {
        val scrollState = rememberScrollState()

        val surfaceModifier = if (isScrollable) modifier.fillMaxSize() else modifier.fillMaxWidth()

        val isDark = isSystemInDarkTheme()
        val surfaceColor = MaterialTheme.colorScheme.surface
        val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

        val bgGradient = remember(isDark, surfaceColor, surfaceContainerLow, primaryContainer, secondaryContainer) {
            if (isDark) {
                Brush.verticalGradient(colors = listOf(surfaceColor, surfaceContainerLow))
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        primaryContainer.copy(alpha = 0.16f),
                        secondaryContainer.copy(alpha = 0.10f),
                        surfaceColor
                    )
                )
            }
        }

        val contentModifier = if (applyBackground) {
            surfaceModifier
                .background(MaterialTheme.colorScheme.surface)
                .background(bgGradient)
        } else {
            surfaceModifier
        }

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (includeDragHandle) {
                ExpressiveDragHandle(modifier = dragHandleModifier)
            }

            val scrollModifier = if (isScrollable) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            }

            Column(modifier = scrollModifier) {
                ExpressiveSheetHeader(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_subtitle),
                    onBack = callbacks.onClose,
                    icon = Icons.Rounded.Settings,
                    actions = if (!isScrollable) {
                        {
                            IconButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(context, SettingsActivity::class.java))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .bouncyClickable {
                                        try {
                                            context.startActivity(Intent(context, SettingsActivity::class.java))
                                        } catch (_: Exception) {}
                                    }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = stringResource(R.string.settings_open_fullscreen),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null
                )

                key(sessionKey) {
                    Spacer(Modifier.height(20.dp))

                    AppearanceSettingsComposable(
                        context = context,
                        onThemeChanged = callbacks.onThemeChanged
                    )

                    Spacer(Modifier.height(24.dp))

                    DisplayScaleSettingsComposable(
                        context = context,
                        onScaleChanged = callbacks.onScaleChanged
                    )

                    Spacer(Modifier.height(24.dp))

                    NavigationSettingsComposable(
                        context = context,
                        onHomePageChanged = callbacks.onHomePageChanged
                    )

                    Spacer(Modifier.height(24.dp))

                    InAppControlsComposable(
                        context = context,
                        onInAppControlsChanged = callbacks.onInAppControlsChanged
                    )

                    Spacer(Modifier.height(24.dp))

                    StartPageSettingsComposable(
                        context = context,
                        onPickStartPageBackground = callbacks.onPickStartPageBackground,
                        onClearStartPageBackground = callbacks.onClearStartPageBackground,
                    )

                    Spacer(Modifier.height(24.dp))

                    PrivacyAndAboutComposable(
                        context = context,
                        onDrmL3EnforcerChanged = callbacks.onDrmL3EnforcerChanged,
                        onUserAgentChanged = callbacks.onUserAgentChanged
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (applyTheme) {
        AABrowserTheme { ScreenBody() }
    } else {
        ScreenBody()
    }
}
