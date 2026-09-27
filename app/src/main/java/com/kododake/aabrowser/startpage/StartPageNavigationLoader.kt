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

package com.kododake.aabrowser.startpage

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.ui.compose.components.NavigationLoadingOverlay

/**
 * Manages the loading transition animation when navigating from the start page,
 * bookmarks, or address bar to a website.
 */
class StartPageNavigationLoader(
    private val loadingOverlayView: ComposeView,
    private val startPageRootView: View,
    private val customBackgroundBitmapProvider: () -> Bitmap? = { null }
) {

    private var pendingCompleteHide: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { finishLoading() }

    val isNavigatingState = mutableStateOf(false)

    /** Whether a navigation transition is currently in progress. */
    var isNavigating: Boolean
        get() = isNavigatingState.value
        private set(value) { isNavigatingState.value = value }

    private var fromStartPage: Boolean = false

    init {
        loadingOverlayView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        loadingOverlayView.setContent {
            NavigationLoadingOverlay(
                visible = isNavigatingState.value,
                customBackgroundBitmap = customBackgroundBitmapProvider()
            )
        }
    }

    /**
     * Starts the loading transition.
     * @param fromStartPage Whether the navigation originated from the start page.
     * @param onComplete Optional callback called when the transition finishes.
     */
    fun beginLoading(fromStartPage: Boolean, onComplete: (() -> Unit)? = null) {
        this.fromStartPage = fromStartPage
        this.pendingCompleteHide = onComplete
        isNavigating = true

        loadingOverlayView.visibility = View.VISIBLE
        loadingOverlayView.animate().cancel()
        loadingOverlayView.alpha = 1f

        if (fromStartPage) {
            startPageRootView.animate().cancel()
            startPageRootView.visibility = View.GONE
        }

        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, 4000L)
    }

    /**
     * Cancels the loading transition immediately (e.g. user pressed back button).
     */
    fun cancelLoading() {
        mainHandler.removeCallbacks(timeoutRunnable)
        pendingCompleteHide = null
        isNavigating = false

        loadingOverlayView.animate().cancel()
        loadingOverlayView.visibility = View.GONE

        if (fromStartPage) {
            startPageRootView.animate().cancel()
            startPageRootView.alpha = 1f
            startPageRootView.visibility = View.VISIBLE
        }
        fromStartPage = false
    }

    /**
     * Called when WebView progress changes. Finishes transition when progress >= 50%.
     */
    fun onWebProgress(progress: Int) {
        if (isNavigating && progress >= 50) {
            finishLoading()
        }
    }

    /**
     * Fades out the loading overlay (and start page if applicable) and notifies caller.
     */
    fun finishLoading() {
        mainHandler.removeCallbacks(timeoutRunnable)
        val onComplete = pendingCompleteHide
        pendingCompleteHide = null

        val wasFromStartPage = fromStartPage
        fromStartPage = false

        if (wasFromStartPage) {
            startPageRootView.visibility = View.GONE
        }

        loadingOverlayView.animate()
            .alpha(0f)
            .setDuration(240L)
            .withEndAction {
                isNavigating = false
                loadingOverlayView.alpha = 1f
                loadingOverlayView.visibility = View.GONE
                onComplete?.invoke()
            }
            .start()
    }

    fun onDestroy() {
        cancelLoading()
    }
}
