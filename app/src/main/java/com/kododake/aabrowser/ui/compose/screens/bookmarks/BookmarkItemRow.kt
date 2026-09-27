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

package com.kododake.aabrowser.ui.compose.screens.bookmarks

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.ui.compose.components.ListGroupPosition
import com.kododake.aabrowser.ui.compose.components.bouncyClickable

@Composable
fun BookmarkItemRow(
    item: BookmarkItemUi,
    position: ListGroupPosition,
    onSelect: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    favicon: Bitmap? = null,
    isDragging: Boolean = false,
    isAnyItemDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val itemShape = if (isDragging) {
        RoundedCornerShape(20.dp)
    } else if (isAnyItemDragging) {
        RoundedCornerShape(16.dp)
    } else {
        when (position) {
            ListGroupPosition.Single -> RoundedCornerShape(28.dp)
            ListGroupPosition.Top -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            ListGroupPosition.Middle -> RoundedCornerShape(8.dp)
            ListGroupPosition.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        }
    }

    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDragging) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val borderColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
    }

    Surface(
        shape = itemShape,
        color = containerColor,
        border = BorderStroke(if (isDragging) 1.5.dp else 1.dp, borderColor),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .bouncyClickable(
                enabled = !isAnyItemDragging,
                onClick = onSelect
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.6f else 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (favicon != null && !favicon.isRecycled) {
                    Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onPin,
                enabled = !isAnyItemDragging
            ) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "Pin to Start Page",
                    tint = if (item.slotIndex >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = onDelete,
                enabled = !isAnyItemDragging
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete Bookmark",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
