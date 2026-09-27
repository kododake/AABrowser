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

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

@Composable
internal fun BmcTabContent(
    onOpenUrl: (String) -> Unit,
    isLandscape: Boolean = false
) {
    val buttonShape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.start_page_bmc_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (isLandscape) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .then(if (isLandscape) Modifier.widthIn(max = 480.dp) else Modifier.fillMaxWidth())
                .padding(top = 10.dp)
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SponsorQrCard {
                Image(
                    painter = painterResource(R.drawable.bmc_qr_code),
                    contentDescription = "Buy Me a Coffee QR",
                    modifier = Modifier.size(92.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = if (isLandscape) Modifier else Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.start_page_bmc_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = buttonShape,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                    color = Color.White,
                    modifier = Modifier
                        .height(44.dp)
                        .width(156.dp)
                        .bouncyClickable(
                            shape = buttonShape,
                            onClick = { onOpenUrl("https://buymeacoffee.com/kododake") }
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.white_button),
                        contentDescription = stringResource(R.string.start_page_buy_me_a_coffee),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
internal fun GithubTabContent(
    qrBitmap: Bitmap?,
    onOpenUrl: (String) -> Unit,
    isLandscape: Boolean = false
) {
    val buttonShape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.settings_sponsors_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (isLandscape) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .then(if (isLandscape) Modifier.widthIn(max = 480.dp) else Modifier.fillMaxWidth())
                .padding(top = 10.dp)
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (qrBitmap != null) {
                SponsorQrCard {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "GitHub Sponsors QR",
                        modifier = Modifier.size(92.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))
            }

            Column(
                modifier = if (isLandscape) Modifier else Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "https://github.com/sponsors/kododake",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = buttonShape,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .height(44.dp)
                        .bouncyClickable(
                            shape = buttonShape,
                            onClick = { onOpenUrl("https://github.com/sponsors/kododake") }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFEC407A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_sponsors_open_github_sponsors),
                            style = ExpressiveTypography.labelLargeEmphasized
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorQrCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        shadowElevation = 0.dp,
        modifier = modifier.size(104.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
