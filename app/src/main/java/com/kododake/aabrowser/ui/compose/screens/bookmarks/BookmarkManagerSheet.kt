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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kododake.aabrowser.ui.compose.components.ExpressiveBottomSheetContainer
import com.kododake.aabrowser.ui.compose.components.ListGroupPosition
import com.kododake.aabrowser.ui.compose.components.rememberReorderableListState
import com.kododake.aabrowser.ui.compose.components.reorderableList

@Composable
fun BookmarkManagerSheet(
    isVisible: Boolean,
    bookmarks: List<BookmarkItemUi>,
    canAddCurrentUrl: Boolean,
    currentUrl: String,
    actions: BookmarkActions,
    faviconProvider: (String) -> Bitmap? = { null },
    animateEnter: Boolean = true,
    keepScrimOnClose: Boolean = false,
    onProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ExpressiveBottomSheetContainer(
        isVisible = isVisible,
        onDismissRequest = actions.onDismiss,
        onDismissFinished = actions.onDismissFinished,
        animateEnter = animateEnter,
        keepScrimOnClose = keepScrimOnClose,
        onProgress = onProgress,
        modifier = modifier
    ) {
        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableListState(
            lazyListState = lazyListState,
            headerCount = 1,
            onMoveItem = { from, to -> actions.onReorderBookmarks(from, to) },
            onDragCommit = { actions.onCommitBookmarkReorder() }
        )

        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxListHeight = (screenHeight - 110.dp).coerceAtLeast(180.dp)

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .reorderableList(reorderState),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item(key = "header") {
                BookmarkSheetHeader(
                    count = bookmarks.size,
                    canAddCurrentUrl = canAddCurrentUrl,
                    actions = actions
                )
                Spacer(Modifier.height(14.dp))
            }

            if (bookmarks.isEmpty()) {
                item(key = "empty") { BookmarkEmptyView() }
            } else {
                itemsIndexed(bookmarks, key = { _, item -> item.url }) { index, item ->
                    val position = when {
                        bookmarks.size == 1 -> ListGroupPosition.Single
                        index == 0 -> ListGroupPosition.Top
                        index == bookmarks.lastIndex -> ListGroupPosition.Bottom
                        else -> ListGroupPosition.Middle
                    }

                    val isDragging = reorderState.draggedKey == item.url

                    BookmarkItemRow(
                        item = item,
                        position = position,
                        onSelect = { actions.onSelectBookmark(item.url) },
                        onPin = { actions.onPinBookmark(item.url, item.slotIndex) },
                        onDelete = { actions.onDeleteBookmark(item.url) },
                        favicon = faviconProvider(item.url),
                        isDragging = isDragging,
                        isAnyItemDragging = reorderState.isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = reorderState.dragOffsetY
                                    scaleX = reorderState.dragScale
                                    scaleY = reorderState.dragScale
                                }
                            }
                            .then(
                                if (isDragging) Modifier
                                else Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null
                                )
                            )
                    )
                }
            }
        }
    }
}
