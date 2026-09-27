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

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.QRUtils
import com.kododake.aabrowser.ui.compose.screens.startpage.StartPageScreenCallbacks
import com.kododake.aabrowser.ui.compose.screens.startpage.StartPageSlotUi
import com.kododake.aabrowser.ui.compose.screens.startpage.StartPageViews

class StartPageManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val bookmarkManager: BookmarkManager,
    private val callbacks: StartPageCallbacks
) {

    interface StartPageCallbacks {
        fun onNavigateToUrl(url: String)
        fun onShowMenuOverlay()
        fun onHideMenuOverlay()
        fun onEnterFullscreen()
        fun onExitFullscreen()
        fun isInFullscreen(): Boolean
        fun getCurrentUrl(): String
        fun resolveThemeColor(attrRes: Int): Int
        fun updateNavigationButtons()
        fun showMenuButtonTemporarily()
        fun loadUrlFromIntent(url: String)
        fun resolveReadableTextColor(bg: Int, pr: Int, fb: Int): Int
    }

    var isShowingStartPage: Boolean = false
    var isStartPagePhotoOnlyMode: Boolean = false

    private val backgroundRenderer = StartPageBackgroundRenderer(
        activity = activity,
        binding = binding,
        resolveThemeColor = callbacks::resolveThemeColor
    )

    val navigationLoader = StartPageNavigationLoader(
        loadingOverlayView = binding.navigationLoadingOverlay,
        startPageRootView = binding.startPageRoot,
        customBackgroundBitmapProvider = { backgroundRenderer.customBackgroundBitmap }
    )

    val isNavigatingState get() = navigationLoader.isNavigatingState
    val isNavigating: Boolean get() = navigationLoader.isNavigating

    private var composeStartPageView: View? = null

    fun onDestroy() {
        navigationLoader.onDestroy()
        backgroundRenderer.onDestroy()
        composeStartPageView?.let { binding.startPageRoot.removeView(it) }
        composeStartPageView = null
    }

    private fun ensureComposeStartPagePopulated() {
        if (composeStartPageView == null) {
            val slots = BrowserPreferences.getStartPageSlots(activity).mapIndexed { index, url ->
                val cleanUrl = url.orEmpty()
                val title = if (cleanUrl.isNotBlank()) bookmarkManager.resolveBookmarkTitle(cleanUrl) else ""
                val label = if (cleanUrl.isNotBlank()) bookmarkManager.displayLabelForUrl(cleanUrl) else ""
                StartPageSlotUi(
                    id = index,
                    index = index,
                    url = url,
                    title = title,
                    label = label
                )
            }
            val hasResumePage = !BrowserPreferences.getLastVisitedUrl(activity).isNullOrBlank()
            val qrBitmap = QRUtils.generateQrCode("https://github.com/sponsors/kododake", 200)

            val screenCallbacks = StartPageScreenCallbacks(
                onNavigate = { url -> callbacks.loadUrlFromIntent(url) },
                onSlotClick = { _, url ->
                    if (url.isNullOrBlank()) {
                        callbacks.onShowMenuOverlay()
                        bookmarkManager.showBookmarkManager()
                    } else {
                        callbacks.loadUrlFromIntent(url)
                    }
                },
                onMoveSlot = { fromIdx, toIdx ->
                    val currentSlots = BrowserPreferences.getStartPageSlots(activity).toMutableList()
                    if (fromIdx in currentSlots.indices && toIdx in currentSlots.indices && fromIdx != toIdx) {
                        val moved = currentSlots.removeAt(fromIdx)
                        currentSlots.add(toIdx, moved)
                        BrowserPreferences.setStartPageSlots(activity, currentSlots)
                    }
                },
                onClearSlot = { idx ->
                    BrowserPreferences.clearStartPageSlot(activity, idx)
                    refreshStartPage()
                },
                onResumeClick = {
                    val last = BrowserPreferences.getLastVisitedUrl(activity)
                    if (!last.isNullOrBlank()) {
                        callbacks.loadUrlFromIntent(last)
                    }
                },
                onPhotoOnlyToggle = {
                    isStartPagePhotoOnlyMode = !isStartPagePhotoOnlyMode
                    applyStartPagePhotoOnlyMode()
                },
                onOpenSponsors = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(activity, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            val view = StartPageViews.createStartPageContent(
                context = activity,
                slots = slots,
                hasResumePage = hasResumePage,
                sponsorsQrBitmap = qrBitmap,
                customBackgroundBitmapState = backgroundRenderer.customBackgroundBitmapState,
                customBackgroundBitmapProvider = { backgroundRenderer.customBackgroundBitmap },
                isNavigatingState = isNavigatingState,
                callbacks = screenCallbacks
            )
            composeStartPageView = view
            binding.startPageRoot.addView(view)
        }
    }


    fun beginNavigationLoading(fromStartPage: Boolean = isShowingStartPage, onComplete: (() -> Unit)? = null) {
        navigationLoader.beginLoading(fromStartPage = fromStartPage, onComplete = onComplete)
    }

    fun cancelNavigationLoading() {
        navigationLoader.cancelLoading()
        if (binding.startPageRoot.visibility == View.VISIBLE) {
            isShowingStartPage = true
            callbacks.updateNavigationButtons()
        }
    }

    fun onProgressChanged(progress: Int) = navigationLoader.onWebProgress(progress)

    fun finishNavigationLoading() = navigationLoader.finishLoading()

    fun showStartPage() {
        val homePageUrl = BrowserPreferences.getHomePageUrl(activity)
        if (!homePageUrl.isNullOrBlank()) {
            val message = activity.getString(R.string.start_page_disabled_by_home_page)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            callbacks.loadUrlFromIntent(homePageUrl)
            return
        }

        if (callbacks.isInFullscreen()) {
            callbacks.onExitFullscreen()
        }

        navigationLoader.cancelLoading()
        isShowingStartPage = true
        isStartPagePhotoOnlyMode = false
        binding.startPageRoot.visibility = View.VISIBLE

        refreshStartPage()
        applyStartPagePhotoOnlyMode()
        callbacks.updateNavigationButtons()
        callbacks.showMenuButtonTemporarily()
    }

    fun hideStartPage(currentPageTitle: String, currentUrl: String) {
        if (!isShowingStartPage && binding.startPageRoot.visibility != View.VISIBLE) {
            return
        }

        isShowingStartPage = false
        isStartPagePhotoOnlyMode = false
        applyStartPagePhotoOnlyMode()
        binding.startPageRoot.visibility = View.GONE

        callbacks.updateNavigationButtons()
        bookmarkManager.refreshBookmarks()
    }

    fun applyStartPagePhotoOnlyMode() {
        val visibility = if (isStartPagePhotoOnlyMode) View.GONE else View.VISIBLE
        composeStartPageView?.visibility = visibility
        val hasCustomBg = !BrowserPreferences.getStartPageBackgroundUri(activity).isNullOrBlank()
        binding.startPageDimOverlay.visibility = if (hasCustomBg && !isStartPagePhotoOnlyMode) View.VISIBLE else View.GONE

        if (!isStartPagePhotoOnlyMode && isShowingStartPage) {
            callbacks.showMenuButtonTemporarily()
        }
    }

    fun refreshStartPage() {
        if (isNavigating) return
        backgroundRenderer.refreshStartPageBackground()
        composeStartPageView?.let { binding.startPageRoot.removeView(it) }
        composeStartPageView = null
        if (isShowingStartPage) {
            ensureComposeStartPagePopulated()
        }
    }

    fun refreshStartPageBackground() {
        backgroundRenderer.refreshStartPageBackground()
    }

    fun handleStartPageBackgroundPicked(uri: Uri?) {
        backgroundRenderer.handleStartPageBackgroundPicked(uri) {
            refreshStartPage()
        }
    }

    fun clearStartPageBackground() {
        backgroundRenderer.clearStartPageBackground {
            refreshStartPage()
        }
    }
}
