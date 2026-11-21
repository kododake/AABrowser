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

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

enum class SponsorTab {
    BUY_ME_A_COFFEE,
    GITHUB_SPONSORS;

    fun next(): SponsorTab = when (this) {
        BUY_ME_A_COFFEE -> GITHUB_SPONSORS
        GITHUB_SPONSORS -> BUY_ME_A_COFFEE
    }
}

@Composable
fun StartPageSponsorsSection(
    githubQrBitmap: Bitmap?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = isSystemInDarkTheme()
    var selectedTab by remember { mutableStateOf(SponsorTab.BUY_ME_A_COFFEE) }

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = stringResource(
                    if (selectedTab == SponsorTab.BUY_ME_A_COFFEE) R.string.start_page_buy_me_a_coffee
                    else R.string.settings_sponsors_tab_github
                ),
                style = ExpressiveTypography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (isLandscape) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            SponsorTabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                isLandscape = isLandscape
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "SponsorTabContent"
            ) { tab ->
                when (tab) {
                    SponsorTab.BUY_ME_A_COFFEE -> {
                        BmcTabContent(
                            onOpenUrl = onOpenUrl,
                            isLandscape = isLandscape
                        )
                    }
                    SponsorTab.GITHUB_SPONSORS -> {
                        GithubTabContent(
                            qrBitmap = githubQrBitmap,
                            onOpenUrl = onOpenUrl,
                            isLandscape = isLandscape
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SponsorTabSelector(
    selectedTab: SponsorTab,
    onTabSelected: (SponsorTab) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val tabs = listOf(
        Triple(SponsorTab.BUY_ME_A_COFFEE, stringResource(R.string.start_page_bmc_tab), R.drawable.ic_bmc_logo),
        Triple(SponsorTab.GITHUB_SPONSORS, stringResource(R.string.start_page_github_tab), R.drawable.ic_github)
    )

    val unselectedContainerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
    }

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier
            .then(if (isLandscape) Modifier.widthIn(max = 480.dp).fillMaxWidth() else Modifier.fillMaxWidth())
            .padding(top = 12.dp)
    ) {
        tabs.forEachIndexed { index, (tab, title, iconRes) ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isSelected = selectedTab == tab
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    }
                    FilledTonalToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onTabSelected(tab) },
                        shapes = shapes,
                        interactionSource = interactionSource,
                        colors = FilledTonalToggleButtonDefaults.colors(
                            containerColor = unselectedContainerColor,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .animateWidth(interactionSource)
                    ) {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = ExpressiveTypography.labelLargeEmphasized,
                            maxLines = 1
                        )
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            state.dismiss()
                            onTabSelected(tab)
                        }
                    )
                }
            )
        }
    }
}
