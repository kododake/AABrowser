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

package com.kododake.aabrowser.bookmarks

import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkActions
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkItemUi
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkManagerSheet

class BookmarkManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val callbacks: BookmarkCallbacks
) {

    interface BookmarkCallbacks {
        fun onNavigateToUrl(url: String)
        fun onRefreshStartPage()
        fun onRebuildSettings()
        fun getCurrentUrl(): String
        fun isShowingStartPage(): Boolean
        fun resolveThemeColor(attrRes: Int): Int
        fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int
        fun isHomePageEnabled(): Boolean
        fun handleHomePagePreferenceChanged()
        fun onReturnToMenuRequested() {}
        fun onDismissOverlaysRequested() {}
        fun showMenuButtonTemporarily()
        fun onSheetProgress(progress: Float) {}
        fun getCurrentPageTitle(): String = ""
    }

    internal val isOpenedFromMenuState = mutableStateOf(false)
    val isOpenedFromMenu: Boolean
        get() = isOpenedFromMenuState.value

    internal val isVisibleState = mutableStateOf(false)
    internal val keepScrimState = mutableStateOf(false)
    internal val bookmarksState = mutableStateOf<List<BookmarkItemUi>>(emptyList())
    internal val canAddCurrentUrlState = mutableStateOf(false)
    internal val currentUrlState = mutableStateOf("")

    init {
        BrowserPreferences.ensureGameBookmarkMigrated(activity)
        setupComposeBookmarks()
    }

    private fun setupComposeBookmarks() {
        BookmarkComposeHelper.setupComposeBookmarks(this, binding, callbacks)
    }

    fun showBookmarkManager(fromMenu: Boolean = false) {
        isOpenedFromMenuState.value = fromMenu
        binding.menuComposeView.visibility = View.GONE
        binding.tabComposeView.visibility = View.GONE
        binding.qrCodeComposeView.visibility = View.GONE
        binding.versionComposeView.visibility = View.GONE
        binding.settingsComposeView.visibility = View.GONE
        binding.menuOverlay.visibility = View.VISIBLE
        binding.bookmarkComposeView.visibility = View.VISIBLE
        isVisibleState.value = true
        refreshBookmarks()
    }

    fun returnToMenu() {
        keepScrimState.value = true
        isVisibleState.value = false
        binding.bookmarkComposeView.visibility = View.GONE
        callbacks.onReturnToMenuRequested()
    }

    fun reorderBookmarks(fromIndex: Int, toIndex: Int) {
        val currentList = bookmarksState.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val moved = currentList.removeAt(fromIndex)
            currentList.add(toIndex, moved)
            bookmarksState.value = currentList
        }
    }

    fun commitBookmarkReorder() {
        BrowserPreferences.setBookmarks(activity, bookmarksState.value.map { it.url })
        callbacks.onRefreshStartPage()
    }

    fun hideBookmarkManager() {
        isOpenedFromMenuState.value = false
        isVisibleState.value = false
    }

    internal fun onBookmarkDismissFinished() {
        val returningToMenu = keepScrimState.value
        keepScrimState.value = false
        if (!isVisibleState.value) {
            binding.bookmarkComposeView.visibility = View.GONE
            if (!returningToMenu) {
                callbacks.onDismissOverlaysRequested()
            }
        }
    }

    fun addBookmarkForCurrentPage() {
        val url = callbacks.getCurrentUrl().trim()
        val isStartPage = callbacks.isShowingStartPage()

        if (!isActiveWebsiteUrl(url) || isStartPage) {
            val message = activity.getString(R.string.start_page_add_current_unavailable)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            return
        }

        val pageTitle = callbacks.getCurrentPageTitle().trim().ifBlank { displayTitleForUrl(url) }
        if (BrowserPreferences.addBookmark(activity, url, pageTitle)) {
            val message = activity.getString(R.string.bookmark_added)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
        } else {
            val message = activity.getString(R.string.bookmark_exists)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun removeBookmark(url: String) {
        if (BrowserPreferences.removeBookmark(activity, url)) {
            val message = activity.getString(R.string.bookmark_removed)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
            callbacks.onRefreshStartPage()
        }
    }

    fun isActiveWebsiteUrl(url: String?): Boolean = BookmarkUrlFormatter.isActiveWebsiteUrl(url)
    fun displayLabelForUrl(url: String): String = BookmarkUrlFormatter.displayLabelForUrl(url)
    fun displayTitleForUrl(url: String): String = BookmarkUrlFormatter.displayTitleForUrl(url)

    fun resolveBookmarkTitle(url: String): String {
        val target = url.trim().trimEnd('/')
        return BrowserPreferences.getBookmarkEntries(activity)
            .firstOrNull { it.url.trim().trimEnd('/') == target }
            ?.title?.takeIf { it.isNotBlank() } ?: displayTitleForUrl(url)
    }

    fun prefetchSiteIcon(url: String?) {
        BookmarkIconUtils.prefetchSiteIcon(activity, url) {
            if (callbacks.isShowingStartPage()) callbacks.onRefreshStartPage()
            refreshBookmarks()
        }
    }

    fun resolveCachedSiteIcon(url: String?): Bitmap? = BookmarkIconUtils.resolveCachedSiteIcon(activity, url) {
        refreshBookmarks()
    }

    fun createSiteIconBadge(
        url: String?,
        sizeDp: Float,
        cornerRadiusDp: Float,
        paddingDp: Float,
        backgroundColor: Int,
        showAddOnEmptyUrl: Boolean = false
    ): View = BookmarkIconUtils.createSiteIconBadge(
        context = activity,
        url = url,
        sizeDp = sizeDp,
        cornerRadiusDp = cornerRadiusDp,
        paddingDp = paddingDp,
        backgroundColor = backgroundColor,
        plusColor = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer),
        showAddOnEmptyUrl = showAddOnEmptyUrl
    )

    fun refreshBookmarks() {
        val currentUrl = callbacks.getCurrentUrl().trim()
        val canUseCurrentPage = !callbacks.isShowingStartPage() && isActiveWebsiteUrl(currentUrl)
        canAddCurrentUrlState.value = canUseCurrentPage
        currentUrlState.value = currentUrl

        val entries = BrowserPreferences.getBookmarkEntries(activity)
        val slots = BrowserPreferences.getStartPageSlots(activity)

        bookmarksState.value = entries.map { entry ->
            val slotIndex = slots.indexOf(entry.url)
            BookmarkItemUi(
                url = entry.url,
                title = entry.title.ifBlank { displayTitleForUrl(entry.url) },
                slotIndex = slotIndex
            )
        }
    }

    fun updateBookmarkTitleIfBetter(url: String, title: String) {
        val newTitle = title.trim()
        if (newTitle.isBlank()) return
        val target = url.trim().trimEnd('/')
        val entries = BrowserPreferences.getBookmarkEntries(activity)
        val existing = entries.firstOrNull { it.url.trim().trimEnd('/') == target } ?: return
        val fallback = displayTitleForUrl(url)
        val canUpgrade = existing.title.isBlank() || existing.title.equals(fallback, true) ||
            existing.title == "ゲーム" || (existing.title != newTitle && !newTitle.equals(fallback, true))
        if (canUpgrade && BrowserPreferences.updateBookmarkTitle(activity, existing.url, newTitle)) {
            refreshBookmarks()
            callbacks.onRefreshStartPage()
        }
    }

    fun showStartPageSlotPicker(url: String) {
        StartPageSlotPickerDialog.show(
            activity = activity,
            url = url,
            isHomePageEnabled = callbacks.isHomePageEnabled(),
            onSlotChanged = {
                refreshBookmarks()
                callbacks.onRefreshStartPage()
            }
        )
    }

    fun setCurrentPageAsHomePage() {
        val url = callbacks.getCurrentUrl().trim()
        if (!isActiveWebsiteUrl(url) || callbacks.isShowingStartPage()) {
            Toast.makeText(activity, R.string.home_page_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        BrowserPreferences.setHomePageUrl(activity, url)
        Toast.makeText(activity, R.string.home_page_set, Toast.LENGTH_SHORT).show()
        callbacks.handleHomePagePreferenceChanged()
    }
}
