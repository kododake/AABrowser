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

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.ui.compose.components.bouncyClickable
import com.kododake.aabrowser.ui.compose.theme.ExpressiveTypography

@Composable
fun ExpressiveConfirmationContent(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hostLabel: String? = null,
    hostValue: String? = null,
    detailMessage: String? = null,
    icon: ImageVector = Icons.Rounded.Warning,
    isDestructive: Boolean = true,
    confirmButtonText: String? = null,
    onConfirm: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    onSecondary: (() -> Unit)? = null,
    dismissButtonText: String = stringResource(android.R.string.cancel),
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.88f).dp
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = title, style = ExpressiveTypography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!hostLabel.isNullOrBlank() && !hostValue.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = hostLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(text = hostValue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                    }
                }
                if (!detailMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = detailMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))

            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val textStyle = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
            val btnPadding = if (isLandscape) PaddingValues(horizontal = 6.dp, vertical = 8.dp) else PaddingValues(horizontal = 12.dp, vertical = 10.dp)

            val cancelBtn: @Composable (Modifier) -> Unit = { btnModifier ->
                val cancelContainer = if (isDestructive) MaterialTheme.colorScheme.primary else Color.Transparent
                val cancelContent = if (isDestructive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                val cancelBorder = if (isDestructive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                DialogActionButton(dismissButtonText, onDismiss, btnModifier, cancelContainer, cancelContent, cancelBorder, textStyle, btnPadding)
            }

            val secondaryBtn: (@Composable (Modifier) -> Unit)? = if (secondaryButtonText != null && onSecondary != null) {
                { btnModifier ->
                    val secContainer = if (isDestructive) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer
                    val secContent = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                    val secBorder = if (isDestructive) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
                    DialogActionButton(secondaryButtonText, onSecondary, btnModifier, secContainer, secContent, secBorder, textStyle, btnPadding)
                }
            } else null

            val confirmBtn: (@Composable (Modifier) -> Unit)? = if (confirmButtonText != null && onConfirm != null) {
                { btnModifier ->
                    val confContainer = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val confContent = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    DialogActionButton(confirmButtonText, onConfirm, btnModifier, confContainer, confContent, null, textStyle, btnPadding)
                }
            } else null

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cancelBtn(Modifier.weight(1f).fillMaxHeight())
                    secondaryBtn?.invoke(Modifier.weight(1f).fillMaxHeight())
                    confirmBtn?.invoke(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    cancelBtn(Modifier.fillMaxWidth())
                    secondaryBtn?.invoke(Modifier.fillMaxWidth())
                    confirmBtn?.invoke(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
) {
    val textContent: @Composable () -> Unit = {
        Text(
            text = text,
            style = textStyle,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
    if (border != null) {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            border = border,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = containerColor, contentColor = contentColor),
            contentPadding = contentPadding,
            modifier = modifier.bouncyClickable(onClick = onClick)
        ) { textContent() }
    } else {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
            contentPadding = contentPadding,
            modifier = modifier.bouncyClickable(onClick = onClick)
        ) { textContent() }
    }
}

@Composable
fun ExpressiveConfirmationDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hostLabel: String? = null,
    hostValue: String? = null,
    detailMessage: String? = null,
    icon: ImageVector = Icons.Rounded.Warning,
    isDestructive: Boolean = true,
    confirmButtonText: String? = null,
    onConfirm: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    onSecondary: (() -> Unit)? = null,
    dismissButtonText: String = stringResource(android.R.string.cancel),
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ExpressiveConfirmationContent(
            title = title,
            message = message,
            modifier = modifier,
            hostLabel = hostLabel,
            hostValue = hostValue,
            detailMessage = detailMessage,
            icon = icon,
            isDestructive = isDestructive,
            confirmButtonText = confirmButtonText,
            onConfirm = onConfirm,
            secondaryButtonText = secondaryButtonText,
            onSecondary = onSecondary,
            dismissButtonText = dismissButtonText,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun CleartextWarningDialog(
    url: String,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    ExpressiveConfirmationDialog(
        title = stringResource(R.string.cleartext_connection_title),
        message = stringResource(R.string.cleartext_connection_message, url),
        isDestructive = true,
        confirmButtonText = stringResource(android.R.string.ok),
        onConfirm = onProceed,
        onDismiss = onDismiss
    )
}
