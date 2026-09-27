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

package com.kododake.aabrowser.ui.compose.screens.startpage

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.ui.compose.components.DynamicWallpaperBackground
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme

data class StartPageScreenCallbacks(
    val onNavigate: (String) -> Unit = {},
    val onSlotClick: (Int, String?) -> Unit = { _, _ -> },
    val onMoveSlot: (Int, Int) -> Unit = { _, _ -> },
    val onClearSlot: (Int) -> Unit = {},
    val onResumeClick: () -> Unit = {},
    val onPhotoOnlyToggle: () -> Unit = {},
    val onOpenSponsors: (String) -> Unit = {}
)

@Composable
fun StartPageScreen(
    context: Context,
    slots: List<StartPageSlotUi>,
    hasResumePage: Boolean,
    sponsorsQrBitmap: Bitmap?,
    customBackgroundBitmap: Bitmap? = null,
    isNavigating: Boolean = false,
    callbacks: StartPageScreenCallbacks = StartPageScreenCallbacks(),
    modifier: Modifier = Modifier
) {
    val shouldHideSponsors = remember { BrowserPreferences.shouldHideSponsors(context) }
    val scrollState = rememberScrollState()

    AABrowserTheme {
        Box(modifier = modifier.fillMaxSize()) {
            DynamicWallpaperBackground(customBackgroundBitmap = customBackgroundBitmap)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    StartPageHeader(
                        onNavigate = callbacks.onNavigate
                    )

                    Spacer(Modifier.height(16.dp))

                    StartPageQuickLinks(
                        slots = slots,
                        onSlotClick = callbacks.onSlotClick,
                        onMoveSlot = callbacks.onMoveSlot,
                        onClearSlot = callbacks.onClearSlot
                    )

                    Spacer(Modifier.height(16.dp))

                    if (!shouldHideSponsors) {
                        Spacer(Modifier.height(16.dp))
                        StartPageSponsorsSection(
                            githubQrBitmap = sponsorsQrBitmap,
                            onOpenUrl = callbacks.onOpenSponsors
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    StartPageActionRow(
                        hasResumePage = hasResumePage,
                        onResumeClick = callbacks.onResumeClick,
                        onPhotoOnlyClick = callbacks.onPhotoOnlyToggle
                    )

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
