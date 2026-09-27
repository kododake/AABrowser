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

package com.kododake.aabrowser.ui.controllers

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.ui.compose.components.ExpressiveWavyProgress
import com.kododake.aabrowser.ui.compose.theme.AABrowserTheme

class ProgressIndicatorController(
    private val composeView: ComposeView
) {
    private var progress by mutableFloatStateOf(0f)
    private var isVisible by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { isVisible = false }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setContent {
            AABrowserTheme {
                ExpressiveWavyProgress(
                    progress = progress,
                    isVisible = isVisible
                )
            }
        }
    }

    fun updateProgress(p: Int) {
        mainHandler.removeCallbacks(hideRunnable)
        if (p in 1..99) {
            if (!isVisible || progress >= 0.95f) {
                progress = 0f
            }
            progress = p / 100f
            isVisible = true
        } else if (p >= 100) {
            if (isVisible) {
                progress = 1f
                mainHandler.postDelayed(hideRunnable, 220L)
            }
        } else {
            isVisible = false
            progress = 0f
        }
    }
}
