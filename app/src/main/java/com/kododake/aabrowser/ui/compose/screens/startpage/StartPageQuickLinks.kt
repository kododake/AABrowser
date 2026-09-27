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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kododake.aabrowser.R

@Composable
fun StartPageQuickLinks(
    slots: List<StartPageSlotUi>,
    onSlotClick: (Int, String?) -> Unit,
    onMoveSlot: (Int, Int) -> Unit = { _, _ -> },
    onClearSlot: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dragState = rememberStartPageQuickLinksDragState(slots, onMoveSlot)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.start_page_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp)
        )

        if (isLandscape) {
            val chunked = dragState.localSlots.chunked(2)
            chunked.forEachIndexed { rowIndex, rowSlots ->
                if (rowIndex > 0) Spacer(Modifier.height(10.dp))
                val isRowDragging = dragState.draggedIndex?.let { it / 2 == rowIndex } == true
                Row(
                    modifier = Modifier.zIndex(if (isRowDragging) 10f else 0f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSlots.forEachIndexed { colIndex, slot ->
                        key(slot.id) {
                            val actualIndex = rowIndex * 2 + colIndex
                            QuickLinkSlotItem(
                                slot = slot,
                                actualIndex = actualIndex,
                                dragState = dragState,
                                onSlotClick = onSlotClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (rowSlots.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            dragState.localSlots.forEachIndexed { actualIndex, slot ->
                if (actualIndex > 0) Spacer(Modifier.height(10.dp))
                key(slot.id) {
                    QuickLinkSlotItem(
                        slot = slot,
                        actualIndex = actualIndex,
                        dragState = dragState,
                        onSlotClick = onSlotClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.start_page_quick_links_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickLinkSlotItem(
    slot: StartPageSlotUi,
    actualIndex: Int,
    dragState: StartPageQuickLinksDragState,
    onSlotClick: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThisDragging = dragState.draggedIndex == actualIndex
    StartPageSlotCard(
        slot = slot,
        isDragging = isThisDragging,
        isAnyDragging = dragState.isAnyDragging,
        dragOffsetProvider = dragState.dragOffsetProvider,
        shiftOffset = dragState.calculateShiftOffset(actualIndex),
        onDragStart = { dragState.onDragStart(actualIndex) },
        onDrag = { delta -> dragState.onDrag(actualIndex, delta) },
        onDragEnd = { dragState.finishDrag() },
        onClick = {
            if (!dragState.isSettling && dragState.draggedIndex == null) {
                onSlotClick(actualIndex, slot.url)
            }
        },
        modifier = modifier
            .zIndex(if (isThisDragging) 10f else 0f)
            .onGloballyPositioned { coords ->
                dragState.updateSlotCenter(actualIndex, coords)
            }
    )
}
