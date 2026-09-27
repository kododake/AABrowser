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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kododake.aabrowser.R

@Composable
fun StartPageSlotCard(
    slot: StartPageSlotUi,
    isDragging: Boolean,
    isAnyDragging: Boolean,
    dragOffsetProvider: () -> Offset,
    shiftOffset: Offset,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val isEmpty = slot.url.isNullOrBlank()
    val cornerShape = RoundedCornerShape(20.dp)

    val gentleSpringSpec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 750f)
    val animatedScale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1.0f, animationSpec = gentleSpringSpec, label = "slotScale")
    val animatedElevation by animateDpAsState(targetValue = if (isDragging) 10.dp else 0.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 750f), label = "slotElevation")
    val animatedShiftOffset by animateOffsetAsState(
        targetValue = if (isDragging) Offset.Zero else shiftOffset,
        animationSpec = if (isAnyDragging) spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 750f) else snap(),
        label = "slotShift"
    )

    val isDark = isSystemInDarkTheme()
    val displayHost = remember(slot.url) {
        slot.url?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")?.trimEnd('/') ?: ""
    }

    val cardColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else if (isEmpty) {
            if (isDark) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
            else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.60f)
        } else {
            if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f)
        },
        animationSpec = tween(150),
        label = "slotCardColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
        } else if (isEmpty) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        },
        animationSpec = tween(150),
        label = "slotBorderColor"
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 10f else 0f)
            .fillMaxWidth()
            .graphicsLayer {
                val effectiveShift = if (isAnyDragging && !isDragging) animatedShiftOffset else Offset.Zero
                val dragOffset = if (isDragging) dragOffsetProvider() else Offset.Zero
                translationX = if (isDragging) dragOffset.x else effectiveShift.x
                translationY = if (isDragging) dragOffset.y else effectiveShift.y
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .shadow(
                elevation = animatedElevation.coerceAtLeast(0.dp),
                shape = cornerShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
            .background(cardColor, cornerShape)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = cornerShape
            )
            .clip(cornerShape)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> currentOnDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = !isDragging,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteIconBadge(url = slot.url)

            Spacer(Modifier.width(14.dp))

            if (!isEmpty) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = slot.title.ifBlank { slot.label.ifBlank { displayHost } },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.start_page_slot_number, slot.index + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = slot.label.ifBlank { displayHost },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.start_page_slot_number, slot.index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.start_page_slot_empty_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
