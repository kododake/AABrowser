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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class StartPageQuickLinksDragState(
    initialSlots: List<StartPageSlotUi>,
    private val onMoveSlot: (Int, Int) -> Unit,
    private val coroutineScope: CoroutineScope
) {
    var localSlots by mutableStateOf(initialSlots)
    var draggedIndex by mutableStateOf<Int?>(null)
    var hoverIndex by mutableStateOf<Int?>(null)
    var isSettling by mutableStateOf(false)
    var liveDragOffset by mutableStateOf(Offset.Zero)
    val dragOffsetAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)
    val slotCenters = mutableStateMapOf<Int, Offset>()

    val isAnyDragging: Boolean get() = draggedIndex != null

    val dragOffsetProvider: () -> Offset = {
        if (isSettling) dragOffsetAnimatable.value else liveDragOffset
    }

    fun updateSlots(slots: List<StartPageSlotUi>) {
        localSlots = slots
    }

    fun onDragStart(actualIndex: Int) {
        if (!isSettling) {
            draggedIndex = actualIndex
            hoverIndex = actualIndex
            liveDragOffset = Offset.Zero
        }
    }

    fun onDrag(actualIndex: Int, delta: Offset) {
        if (!isSettling && draggedIndex == actualIndex) {
            liveDragOffset += delta
            updateHoverTarget(actualIndex, liveDragOffset)
        }
    }

    fun updateSlotCenter(actualIndex: Int, coords: LayoutCoordinates) {
        if (draggedIndex == null && !isSettling) {
            val rootPos = coords.positionInRoot()
            val size = coords.size
            val newCenter = Offset(
                rootPos.x + size.width / 2f,
                rootPos.y + size.height / 2f
            )
            val current = slotCenters[actualIndex]
            if (current == null || (current - newCenter).getDistanceSquared() > 1f) {
                slotCenters[actualIndex] = newCenter
            }
        }
    }

    private fun updateHoverTarget(from: Int, currentOffset: Offset) {
        val fromCenter = slotCenters[from] ?: return
        val currentCenter = fromCenter + currentOffset

        val currentTarget = hoverIndex ?: from
        val currentDistSq = slotCenters[currentTarget]?.let { center ->
            val dx = center.x - currentCenter.x
            val dy = center.y - currentCenter.y
            dx * dx + dy * dy
        } ?: Float.MAX_VALUE

        val nearestEntry = slotCenters.minByOrNull { (_, center) ->
            val dx = center.x - currentCenter.x
            val dy = center.y - currentCenter.y
            dx * dx + dy * dy
        } ?: return

        val candidateKey = nearestEntry.key
        val candidateCenter = nearestEntry.value
        val candidateDistSq = run {
            val dx = candidateCenter.x - currentCenter.x
            val dy = candidateCenter.y - currentCenter.y
            dx * dx + dy * dy
        }

        if (candidateKey == currentTarget || candidateDistSq < currentDistSq * 0.72f) {
            hoverIndex = candidateKey
        }
    }

    fun calculateShiftOffset(gridIndex: Int): Offset {
        val from = draggedIndex ?: return Offset.Zero
        val to = hoverIndex ?: from
        if (gridIndex == from || from == to) return Offset.Zero

        val targetSlot = when {
            from < to && gridIndex in (from + 1)..to -> gridIndex - 1
            from > to && gridIndex in to until from -> gridIndex + 1
            else -> gridIndex
        }

        if (targetSlot == gridIndex) return Offset.Zero

        val currentCenter = slotCenters[gridIndex] ?: return Offset.Zero
        val targetCenter = slotCenters[targetSlot] ?: return Offset.Zero
        return targetCenter - currentCenter
    }

    fun finishDrag() {
        val from = draggedIndex ?: return
        if (isSettling) return
        val to = hoverIndex ?: from

        isSettling = true
        coroutineScope.launch {
            val fromCenter = slotCenters[from] ?: Offset.Zero
            val toCenter = slotCenters[to] ?: fromCenter
            val targetOffset = toCenter - fromCenter

            dragOffsetAnimatable.snapTo(liveDragOffset)
            dragOffsetAnimatable.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(
                    durationMillis = 140,
                    easing = FastOutSlowInEasing
                )
            )

            if (to != from) {
                val updated = localSlots.toMutableList()
                val moved = updated.removeAt(from)
                updated.add(to, moved)
                localSlots = updated.mapIndexed { idx, item -> item.copy(index = idx) }
                onMoveSlot(from, to)
            }

            draggedIndex = null
            hoverIndex = null
            liveDragOffset = Offset.Zero
            isSettling = false
        }
    }
}

@Composable
fun rememberStartPageQuickLinksDragState(
    slots: List<StartPageSlotUi>,
    onMoveSlot: (Int, Int) -> Unit
): StartPageQuickLinksDragState {
    val coroutineScope = rememberCoroutineScope()
    val currentOnMoveSlot by rememberUpdatedState(onMoveSlot)
    val state = remember {
        StartPageQuickLinksDragState(
            initialSlots = slots,
            onMoveSlot = { from, to -> currentOnMoveSlot(from, to) },
            coroutineScope = coroutineScope
        )
    }
    LaunchedEffect(slots) {
        if (!state.isAnyDragging && !state.isSettling) {
            state.updateSlots(slots)
        }
    }
    return state
}
