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

package com.kododake.aabrowser.settings

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.ui.compose.screens.settings.SettingsScreen

/**
 * Facade bridging Android View containers to the Material 3 Expressive Compose SettingsScreen.
 */
object SettingsViews {

    fun createSettingsContent(
        context: Context,
        includeDragHandle: Boolean = false,
        isScrollable: Boolean = false,
        callbacks: SettingsCallbacks = SettingsCallbacks()
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SettingsScreen(
                    context = context,
                    includeDragHandle = includeDragHandle,
                    isScrollable = isScrollable,
                    callbacks = callbacks
                )
            }
        }
    }

    fun createSettingsActivityView(context: Context): View = createSettingsContent(
        context = context,
        includeDragHandle = false,
        isScrollable = true,
        callbacks = SettingsCallbacks(
            onClose = { (context as? Activity)?.finish() },
            onThemeChanged = { (context as? Activity)?.recreate() },
            onScaleChanged = { (context as? Activity)?.recreate() },
            onHomePageChanged = { (context as? Activity)?.recreate() },
            onInAppControlsChanged = { (context as? Activity)?.recreate() },
            onShieldsChanged = { (context as? Activity)?.recreate() }
        )
    )

    fun setup(
        composeView: ComposeView,
        isVisibleState: androidx.compose.runtime.State<Boolean>,
        animateEnterProvider: () -> Boolean = { true },
        callbacks: SettingsCallbacks = SettingsCallbacks(),
        onDismissFinished: () -> Unit = {},
        onProgress: (Float) -> Unit = {}
    ) {
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.kododake.aabrowser.ui.compose.screens.settings.SettingsSheet(
                    isVisible = isVisibleState.value,
                    context = context,
                    callbacks = callbacks,
                    onClose = { callbacks.onClose() },
                    onDismiss = { callbacks.onDismiss() },
                    onDismissFinished = onDismissFinished,
                    animateEnter = animateEnterProvider(),
                    onProgress = onProgress
                )
            }
        }
    }

    fun createSettingsSheetContent(
        context: Context,
        isVisibleState: androidx.compose.runtime.State<Boolean>,
        callbacks: SettingsCallbacks = SettingsCallbacks(),
        onDismissFinished: () -> Unit = {},
        onProgress: (Float) -> Unit = {}
    ): View {
        return ComposeView(context).also { view ->
            setup(
                composeView = view,
                isVisibleState = isVisibleState,
                callbacks = callbacks,
                onDismissFinished = onDismissFinished,
                onProgress = onProgress
            )
        }
    }
}

