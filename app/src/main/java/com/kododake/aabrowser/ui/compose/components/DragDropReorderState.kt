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

package com.kododake.aabrowser.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * State holder managing smooth drag-and-drop reordering for Compose LazyColumn lists.
 * Supports header offsets so that in-list headers can scroll naturally without affecting reordering indices.
 */
class DragDropReorderState(
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
    val headerCount: Int = 0,
    private val onMoveItemState: State<(fromIndex: Int, toIndex: Int) -> Unit>,
    private val onDragCommitState: State<() -> Unit>
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    var dragScale by mutableFloatStateOf(1f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var hasMoved = false
    private var autoScrollJob: Job? = null
    private var settleJob: Job? = null

    fun onDragStart(downOffset: Offset) {
        settleJob?.cancel()
        settleJob = null
        LazyListScrollPositionHelper.clearLastKnownFirstItemKey(lazyListState)
        val layoutInfo = lazyListState.layoutInfo
        val touchedItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            downOffset.y.toInt() in item.offset..(item.offset + item.size)
        } ?: return

        if (touchedItem.index < headerCount || touchedItem.key == "header") return

        draggedKey = touchedItem.key
        draggedIndex = touchedItem.index
        dragOffsetY = 0f
        dragScale = 1.03f
        hasMoved = false
        isDragging = true
    }

    fun onDrag(dragAmountY: Float) {
        val key = draggedKey ?: return
        LazyListScrollPositionHelper.clearLastKnownFirstItemKey(lazyListState)

        dragOffsetY += dragAmountY

        val layoutInfo = lazyListState.layoutInfo
        val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return

        val currentItemCenter = (draggedItem.offset + dragOffsetY) + (draggedItem.size / 2f)

        val targetItem = if (dragOffsetY > 0f) {
            layoutInfo.visibleItemsInfo
                .filter { it.index > draggedItem.index && it.index >= headerCount && currentItemCenter > (it.offset + it.size / 2f) }
                .maxByOrNull { it.index }
        } else if (dragOffsetY < 0f) {
            layoutInfo.visibleItemsInfo
                .filter { it.index < draggedItem.index && it.index >= headerCount && currentItemCenter < (it.offset + it.size / 2f) }
                .minByOrNull { it.index }
        } else {
            null
        }

        if (targetItem != null && targetItem.index >= headerCount) {
            val fromLazyIndex = draggedItem.index
            val toLazyIndex = targetItem.index
            if (fromLazyIndex != toLazyIndex) {
                val fromDataIndex = fromLazyIndex - headerCount
                val toDataIndex = toLazyIndex - headerCount
                if (fromDataIndex >= 0 && toDataIndex >= 0) {
                    LazyListScrollPositionHelper.clearLastKnownFirstItemKey(lazyListState)
                    onMoveItemState.value.invoke(fromDataIndex, toDataIndex)
                    hasMoved = true

                    val offsetDiff = (targetItem.offset - draggedItem.offset).toFloat()
                    dragOffsetY -= offsetDiff
                    draggedIndex = toLazyIndex
                }
            }
        }

        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val edgeThreshold = 60f
        val draggedTop = draggedItem.offset + dragOffsetY

        val scrollDelta = when {
            draggedTop < viewportStart + edgeThreshold -> -15f
            draggedTop + draggedItem.size > viewportEnd - edgeThreshold -> 15f
            else -> 0f
        }

        if (scrollDelta != 0f && autoScrollJob?.isActive != true) {
            autoScrollJob = coroutineScope.launch {
                lazyListState.scrollBy(scrollDelta)
            }
        }
    }

    fun onDragEnd() {
        LazyListScrollPositionHelper.clearLastKnownFirstItemKey(lazyListState)
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (!isDragging || draggedKey == null) return

        val hadMove = hasMoved
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            try {
                val anim = Animatable(dragOffsetY)
                val scaleAnim = Animatable(dragScale)
                launch {
                    scaleAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) {
                        dragScale = value
                    }
                }
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.8f
                    )
                ) {
                    dragOffsetY = value
                }
            } finally {
                if (coroutineContext.isActive) {
                    LazyListScrollPositionHelper.clearLastKnownFirstItemKey(lazyListState)
                    draggedKey = null
                    draggedIndex = null
                    dragOffsetY = 0f
                    dragScale = 1f
                    isDragging = false

                    if (hadMove) {
                        onDragCommitState.value.invoke()
                    }
                }
            }
        }
    }
}

/**
 * Modifier extension to attach drag-and-drop reordering gesture to a LazyColumn.
 */
fun Modifier.reorderableList(reorderState: DragDropReorderState): Modifier =
    this.pointerInput(reorderState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> reorderState.onDragStart(offset) },
            onDrag = { change, dragAmount ->
                change.consume()
                reorderState.onDrag(dragAmount.y)
            },
            onDragEnd = { reorderState.onDragEnd() },
            onDragCancel = { reorderState.onDragEnd() }
        )
    }

@Composable
fun rememberReorderableListState(
    lazyListState: LazyListState,
    headerCount: Int = 0,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragCommit: () -> Unit = {}
): DragDropReorderState {
    val scope = rememberCoroutineScope()
    val currentOnMoveItem = rememberUpdatedState(onMoveItem)
    val currentOnDragCommit = rememberUpdatedState(onDragCommit)

    return remember(lazyListState, headerCount) {
        DragDropReorderState(
            lazyListState = lazyListState,
            coroutineScope = scope,
            headerCount = headerCount,
            onMoveItemState = currentOnMoveItem,
            onDragCommitState = currentOnDragCommit
        )
    }
}

/**
 * Suppresses Compose LazyList's internal scroll-following behavior during reordering.
 */
private object LazyListScrollPositionHelper {
    private val scrollPositionField by lazy {
        runCatching {
            LazyListState::class.java.getDeclaredField("scrollPosition").apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    private val lastKeyField by lazy {
        runCatching {
            Class.forName("androidx.compose.foundation.lazy.LazyListScrollPosition")
                .getDeclaredField("lastKnownFirstItemKey").apply {
                    isAccessible = true
                }
        }.getOrNull()
    }

    fun clearLastKnownFirstItemKey(lazyListState: LazyListState) {
        try {
            val scrollPos = scrollPositionField?.get(lazyListState) ?: return
            lastKeyField?.set(scrollPos, null)
        } catch (_: Throwable) {
        }
    }
}
