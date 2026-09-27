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

package com.kododake.aabrowser.ui.compose.screens.version

import android.content.Context
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object VersionViews {

    fun setup(
        composeView: ComposeView,
        isVisibleState: State<Boolean>,
        isCheckingState: State<Boolean>,
        latestVersionState: State<String?>,
        installedVersion: String,
        releaseUrlState: State<String?>,
        animateEnterProvider: () -> Boolean = { true },
        onOpenRelease: (String) -> Unit,
        onClose: () -> Unit,
        onDismissFinished: () -> Unit,
        onDismiss: () -> Unit = onClose,
        onProgress: (Float) -> Unit = {}
    ) {
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VersionCheckSheet(
                    isVisible = isVisibleState.value,
                    isChecking = isCheckingState.value,
                    latestVersion = latestVersionState.value,
                    installedVersion = installedVersion,
                    releaseUrl = releaseUrlState.value,
                    onOpenRelease = onOpenRelease,
                    onClose = onClose,
                    onDismissFinished = onDismissFinished,
                    onDismiss = onDismiss,
                    animateEnter = animateEnterProvider(),
                    onProgress = onProgress
                )
            }
        }
    }

    fun createVersionCheckSheetContent(
        context: Context,
        isVisibleState: State<Boolean>,
        isCheckingState: State<Boolean>,
        latestVersionState: State<String?>,
        installedVersion: String,
        releaseUrlState: State<String?>,
        onOpenRelease: (String) -> Unit,
        onClose: () -> Unit,
        onDismissFinished: () -> Unit,
        onDismiss: () -> Unit = onClose,
        onProgress: (Float) -> Unit = {}
    ): View {
        return ComposeView(context).also { view ->
            setup(
                composeView = view,
                isVisibleState = isVisibleState,
                isCheckingState = isCheckingState,
                latestVersionState = latestVersionState,
                installedVersion = installedVersion,
                releaseUrlState = releaseUrlState,
                onOpenRelease = onOpenRelease,
                onClose = onClose,
                onDismissFinished = onDismissFinished,
                onDismiss = onDismiss,
                onProgress = onProgress
            )
        }
    }
}

