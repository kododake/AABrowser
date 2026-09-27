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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

enum class SponsorTab {
    BUY_ME_A_COFFEE,
    GITHUB_SPONSORS
}

@Composable
fun StartPageSponsorsSection(
    githubQrBitmap: Bitmap?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: SponsorTab = SponsorTab.BUY_ME_A_COFFEE
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = isSystemInDarkTheme()
    var selectedTab by remember { mutableStateOf(initialTab) }

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        ),
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

@Composable
private fun SponsorTabSelector(
    selectedTab: SponsorTab,
    onTabSelected: (SponsorTab) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = modifier
            .then(if (isLandscape) Modifier.widthIn(max = 480.dp).fillMaxWidth() else Modifier.fillMaxWidth())
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isBmc = selectedTab == SponsorTab.BUY_ME_A_COFFEE
        val isGithub = selectedTab == SponsorTab.GITHUB_SPONSORS

        val bmcShape = RoundedCornerShape(
            topStart = 22.dp,
            bottomStart = 22.dp,
            topEnd = 8.dp,
            bottomEnd = 8.dp
        )
        val githubShape = RoundedCornerShape(
            topStart = 8.dp,
            bottomStart = 8.dp,
            topEnd = 22.dp,
            bottomEnd = 22.dp
        )

        SponsorTabButton(
            title = stringResource(R.string.start_page_bmc_tab),
            iconRes = R.drawable.ic_bmc_logo,
            isSelected = isBmc,
            isDark = isDark,
            shape = bmcShape,
            onClick = { onTabSelected(SponsorTab.BUY_ME_A_COFFEE) },
            modifier = Modifier.weight(1f)
        )

        SponsorTabButton(
            title = stringResource(R.string.start_page_github_tab),
            iconRes = R.drawable.ic_github,
            isSelected = isGithub,
            isDark = isDark,
            shape = githubShape,
            onClick = { onTabSelected(SponsorTab.GITHUB_SPONSORS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SponsorTabButton(
    title: String,
    iconRes: Int,
    isSelected: Boolean,
    isDark: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)
    } else if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
    }

    Surface(
        modifier = modifier
            .height(44.dp)
            .bouncyClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
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
    }
}
