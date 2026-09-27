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

package com.kododake.aabrowser.ui.compose.screens.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.ExpressiveBottomSheetContainer

@Composable
fun BrowserMenuSheet(
    stateHolder: MenuStateHolder,
    actions: MenuActions,
    modifier: Modifier = Modifier
) {
    val isReturning = stateHolder.isReturningFromSubscreen
    if (isReturning) {
        stateHolder.isReturningFromSubscreen = false
    }

    ExpressiveBottomSheetContainer(
        isVisible = stateHolder.isMenuVisible,
        onDismissRequest = actions.onClose,
        onDismissFinished = actions.onDismissFinished,
        onProgress = actions.onProgress,
        isReturning = isReturning,
        modifier = modifier
    ) {
        val maxHeight = dimensionResource(R.dimen.menu_max_height)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuHeaderComponent(
                pageTitle = stateHolder.pageTitle,
                onClose = actions.onClose
            )

            Spacer(Modifier.height(12.dp))

            MenuAddressBar(
                url = stateHolder.url,
                onNavigate = actions.onNavigate
            )

            Spacer(Modifier.height(12.dp))

            MenuNavigationRow(
                canGoBack = stateHolder.canGoBack,
                canGoForward = stateHolder.canGoForward,
                canReload = stateHolder.canReload,
                onBack = actions.onBack,
                onForward = actions.onForward,
                onReload = actions.onReload
            )

            Spacer(Modifier.height(10.dp))

            MenuQuickActionsGrid(
                onBookmarks = actions.onBookmarks,
                onQrCode = actions.onQrCode,
                canQrCode = stateHolder.canQrCode,
                onSettings = actions.onSettings,
                onHome = actions.onHome,
                onTabs = actions.onTabs,
                onNewTab = actions.onNewTab
            )

            Spacer(Modifier.height(10.dp))

            MenuDesktopSwitchCard(
                isDesktopMode = stateHolder.isDesktopMode,
                onDesktopToggle = actions.onDesktopToggle
            )

            Spacer(Modifier.height(8.dp))

            MenuFullscreenSwitchCard(
                isFullscreenMode = stateHolder.isFullscreenMode,
                onFullscreenToggle = actions.onFullscreenToggle
            )

            Spacer(Modifier.height(8.dp))

            MenuShieldsSwitchCard(
                isShieldsEnabled = stateHolder.isShieldsEnabled,
                siteHost = stateHolder.shieldsSiteHost,
                isShieldsOnForSite = stateHolder.isShieldsOnForSite,
                onShieldsSiteToggle = actions.onShieldsSiteToggle
            )

            Spacer(Modifier.height(4.dp))

            MenuFooter(
                versionName = stateHolder.versionName,
                onCheckUpdate = actions.onCheckUpdate,
                onGitHub = actions.onGitHub
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
