package com.kododake.aabrowser.bookmarks

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.data.SiteIconCache
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.navigation.UrlSafetyCoordinator
import com.kododake.aabrowser.ui.adapters.BookmarkAdapter

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
    }

    private val bookmarkAdapter: BookmarkAdapter by lazy {
        BookmarkAdapter(
            bookmarkManager = this,
            onBookmarkClick = { url ->
                callbacks.onNavigateToUrl(url)
            },
            onPinClick = { url, slot ->
                if (slot >= 0) {
                    BrowserPreferences.clearStartPageSlot(activity, slot)
                    Toast.makeText(activity, R.string.start_page_slot_removed, Toast.LENGTH_SHORT).show()
                    refreshBookmarks()
                    callbacks.onRefreshStartPage()
                } else {
                    showStartPageSlotPicker(url)
                }
            },
            onDeleteClick = { url ->
                removeBookmark(url)
            },
            onReordered = { newList ->
                val currentSlots = BrowserPreferences.getStartPageSlots(activity)
                val activeIndices = currentSlots.mapIndexedNotNull { index, url ->
                    if (!url.isNullOrBlank()) index else null
                }
                val activeUrls = currentSlots.filterNotNull().filter { it.isNotBlank() }
                
                val sortedActiveUrls = activeUrls.sortedBy { url ->
                    val idx = newList.indexOf(url)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }
                
                activeIndices.forEachIndexed { i, slotIndex ->
                    if (i < sortedActiveUrls.size) {
                        BrowserPreferences.setStartPageSlot(activity, slotIndex, sortedActiveUrls[i])
                    } else {
                        BrowserPreferences.clearStartPageSlot(activity, slotIndex)
                    }
                }

                BrowserPreferences.setBookmarks(activity, newList)
                refreshBookmarks()
                callbacks.onRefreshStartPage()
            },
            resolveThemeColor = callbacks::resolveThemeColor,
            isHomePageEnabled = callbacks::isHomePageEnabled
        )
    }

    fun addBookmarkForCurrentPage() {
        val url = callbacks.getCurrentUrl().trim()
        val isStartPage = callbacks.isShowingStartPage()
        
        if (!isActiveWebsiteUrl(url) || isStartPage) {
            val message = activity.getString(R.string.start_page_add_current_unavailable)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            return
        }
        
        val normalizedUrl = UrlSafetyCoordinator.normalizeBookmarkKey(url)
        val existing = BrowserPreferences.getBookmarks(activity)
        val alreadyStored = existing.any { UrlSafetyCoordinator.bookmarksReferToSamePage(it, normalizedUrl) }
        if (alreadyStored) {
            val message = activity.getString(R.string.bookmark_exists)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            return
        }
        if (BrowserPreferences.addBookmark(activity, normalizedUrl)) {
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

    fun showBookmarkManager() {
        val views = listOf(
            binding.menuScroll,
            binding.tabManagerRoot,
            binding.qrCodeViewRoot,
            binding.checkLatestViewRoot,
            binding.settingsViewRoot
        )
        views.forEach { view ->
            view.visibility = View.GONE
        }
        binding.bookmarkManagerRoot.visibility = View.VISIBLE
        refreshBookmarks()
    }

    fun hideBookmarkManager() {
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    fun isActiveWebsiteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val scheme = runCatching { android.net.Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    fun displayLabelForUrl(url: String): String {
        return try {
            java.net.URI(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    fun displayTitleForUrl(url: String): String {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isBlank()) {
            return displayLabelForUrl(url)
        }

        // 1. Remove common prefixes
        val cleanedHost = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

        // 2. Identify the main domain name part, taking double-TLDs (e.g. .co.jp, .org.uk) into account
        val parts = cleanedHost.split('.')
        val size = parts.size

        val mainDomain = if (size >= 3) {
            val subTld = parts[size - 2]
            val tld = parts[size - 1]
            val isDoubleTld = (subTld == "co" || subTld == "ne" || subTld == "ac" || subTld == "org" || subTld == "go" || subTld == "or") && tld.length == 2
            if (isDoubleTld) {
                parts[size - 3]
            } else {
                parts[size - 2]
            }
        } else if (size == 2) {
            parts[0]
        } else {
            cleanedHost
        }

        // 3. Resolve the exact root domain for well-known mappings (prevents phishing subdomains mapping)
        val normalizedRoot = if (size >= 3) {
            val subTld = parts[size - 2]
            val tld = parts[size - 1]
            val isDoubleTld = (subTld == "co" || subTld == "ne" || subTld == "ac" || subTld == "org" || subTld == "go" || subTld == "or") && tld.length == 2
            if (isDoubleTld) {
                "${parts[size - 3]}.$subTld.$tld"
            } else {
                "${parts[size - 2]}.$tld"
            }
        } else {
            cleanedHost
        }

        val mapped = when (normalizedRoot) {
            "google.com", "google.co.jp" -> "Google"
            "youtube.com" -> "YouTube"
            "duckduckgo.com" -> "DuckDuckGo"
            "weather.com" -> "Weather"
            else -> null
        }
        if (mapped != null) {
            return mapped
        }

        // 4. Fallback to title-casing the main domain name part
        val segment = mainDomain.split('-', '_').firstOrNull().orEmpty()
        if (segment.isBlank()) {
            return displayLabelForUrl(url)
        }
        return segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun prefetchSiteIcon(url: String?) {
        if (!isActiveWebsiteUrl(url)) {
            return
        }
        SiteIconCache.prefetchIconIfNeeded(activity, url) { bitmap ->
            if (bitmap != null) {
                if (callbacks.isShowingStartPage()) {
                    callbacks.onRefreshStartPage()
                }
                val currentBookmarks = bookmarkAdapter.currentList
                val index = currentBookmarks.indexOf(url)
                if (index >= 0) {
                    bookmarkAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    fun resolveCachedSiteIcon(url: String?): android.graphics.Bitmap? {
        val cached = SiteIconCache.getCachedIcon(activity, url)
        if (cached == null && !url.isNullOrBlank()) {
            prefetchSiteIcon(url)
        }
        return cached
    }

    fun createSiteIconBadge(url: String?, sizeDp: Float, cornerRadiusDp: Float, paddingDp: Float, backgroundColor: Int, showAddOnEmptyUrl: Boolean = false): View {
        val density = activity.resources.displayMetrics.density
        val cachedIcon = resolveCachedSiteIcon(url)
        return android.widget.FrameLayout(activity).apply {
            val sizePx = (sizeDp * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(sizePx, sizePx)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusDp * density
                setColor(backgroundColor)
            }
            addView(android.widget.ImageView(activity).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val p = (paddingDp * density).toInt()
                setPadding(p, p, p, p)
                if (cachedIcon != null) {
                    setImageBitmap(cachedIcon)
                } else {
                    setImageResource(R.drawable.public_24px)
                }
                visibility = if (showAddOnEmptyUrl && url.isNullOrBlank()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            })
            if (showAddOnEmptyUrl) {
                addView(com.google.android.material.textview.MaterialTextView(activity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
                    gravity = android.view.Gravity.CENTER
                    text = "+"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(callbacks.resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    visibility = if (url.isNullOrBlank()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                })
            }
        }
    }

    fun refreshBookmarks() {
        if (binding.bookmarkManagerList.adapter == null) {
            binding.bookmarkManagerList.layoutManager = LinearLayoutManager(activity)
            binding.bookmarkManagerList.adapter = bookmarkAdapter
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    bookmarkAdapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }
                
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                
                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    bookmarkAdapter.onReorderedComplete()
                }
            }).attachToRecyclerView(binding.bookmarkManagerList)
        }

        val currentUrl = callbacks.getCurrentUrl()
        val canUseCurrentPage = !callbacks.isShowingStartPage() && isActiveWebsiteUrl(currentUrl)
        val homePageEnabled = callbacks.isHomePageEnabled()

        binding.buttonBookmarkAdd.isEnabled = canUseCurrentPage
        binding.buttonBookmarkAdd.alpha = if (canUseCurrentPage) 1.0f else 0.6f
        binding.buttonBookmarkStartPageAdd.isEnabled = canUseCurrentPage && !homePageEnabled
        binding.buttonBookmarkStartPageAdd.alpha = if (canUseCurrentPage && !homePageEnabled) 1.0f else 0.6f
        binding.buttonBookmarkSetHomePage.isEnabled = canUseCurrentPage
        binding.buttonBookmarkSetHomePage.alpha = if (canUseCurrentPage) 1.0f else 0.6f

        bookmarkAdapter.submitList(BrowserPreferences.getBookmarks(activity).toList()) {
            bookmarkAdapter.notifyDataSetChanged()
        }
    }

    fun showStartPageSlotPicker(url: String) {
        if (callbacks.isHomePageEnabled()) {
            Toast.makeText(activity, R.string.start_page_add_disabled_by_home_page, Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = BrowserPreferences.formatNavigableUrl(url)
        val slots = BrowserPreferences.getStartPageSlots(activity)
        val existingSlot = BrowserPreferences.findStartPageSlot(activity, normalizedUrl)
        var selectedSlot = when {
            existingSlot >= 0 -> existingSlot
            else -> slots.indexOfFirst { it.isNullOrBlank() }.takeIf { it >= 0 } ?: 0
        }
        val slotLabels = Array(BrowserPreferences.MAX_START_PAGE_SITES) { index ->
            val slotUrl = slots.getOrNull(index)
            val summary = if (slotUrl.isNullOrBlank()) activity.getString(R.string.start_page_slot_empty_title) else displayLabelForUrl(slotUrl)
            "${activity.getString(R.string.start_page_slot_number, index + 1)} - $summary"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.start_page_slot_picker_title)
            .setSingleChoiceItems(slotLabels, selectedSlot) { _, which ->
                selectedSlot = which
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.start_page_slot_picker_save) { _, _ ->
                BrowserPreferences.addBookmark(activity, normalizedUrl)
                BrowserPreferences.setStartPageSlot(activity, selectedSlot, normalizedUrl)
                refreshBookmarks()
                callbacks.onRefreshStartPage()
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.start_page_slot_saved,
                        activity.getString(R.string.start_page_slot_number, selectedSlot + 1)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .apply {
                if (existingSlot >= 0) {
                    setNeutralButton(R.string.start_page_slot_picker_remove) { _, _ ->
                        BrowserPreferences.clearStartPageSlot(activity, existingSlot)
                        refreshBookmarks()
                        callbacks.onRefreshStartPage()
                        Toast.makeText(
                            activity,
                            R.string.start_page_slot_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
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
