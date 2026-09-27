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

package com.kododake.aabrowser.data.prefs

import android.content.Context
import com.kododake.aabrowser.bookmarks.BookmarkUrlFormatter
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkEntry
import org.json.JSONArray
import org.json.JSONObject

object BookmarkPreferences {
    const val PREFS_NAME = "browser_prefs"
    const val MAX_START_PAGE_SITES = StartPagePreferences.MAX_START_PAGE_SITES

    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_GAME_BOOKMARK_MIGRATED = "migrated_game_bookmark_v1"

    const val GAME_BOOKMARK_URL = "https://kododake.com/game/"

    val DEFAULT_BOOKMARK_ENTRIES = listOf(
        BookmarkEntry("https://www.google.com", "Google"),
        BookmarkEntry("https://youtube.com", "YouTube"),
        BookmarkEntry("https://duckduckgo.com", "DuckDuckGo"),
        BookmarkEntry("https://keepandroidopen.org", "Keep Android Open"),
        BookmarkEntry(GAME_BOOKMARK_URL, "Kododake Games")
    )

    val DEFAULT_BOOKMARKS: List<String> = DEFAULT_BOOKMARK_ENTRIES.map { it.url }

    @Volatile
    private var cachedBookmarkEntries: List<BookmarkEntry>? = null

    fun getBookmarkEntries(context: Context): List<BookmarkEntry> {
        val cached = cachedBookmarkEntries
        if (cached != null) return cached

        val entries = loadBookmarkEntries(context)
        val result = if (entries.isEmpty()) {
            persistBookmarkEntries(context, DEFAULT_BOOKMARK_ENTRIES)
            DEFAULT_BOOKMARK_ENTRIES
        } else {
            entries
        }
        cachedBookmarkEntries = result
        return result
    }

    fun getBookmarks(context: Context): List<String> = getBookmarkEntries(context).map { it.url }

    fun setBookmarkEntries(context: Context, entries: List<BookmarkEntry>) {
        cachedBookmarkEntries = entries
        persistBookmarkEntries(context, entries)
    }

    fun setBookmarks(context: Context, bookmarks: List<String>) {
        val current = getBookmarkEntries(context).associateBy { it.url }
        val updated = bookmarks.map { url ->
            current[url] ?: BookmarkEntry(url, BookmarkUrlFormatter.displayTitleForUrl(url))
        }
        setBookmarkEntries(context, updated)
    }

    fun ensureGameBookmarkMigrated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_GAME_BOOKMARK_MIGRATED, false)) return

        val serializedBookmarks = prefs.getString(KEY_BOOKMARKS, null)
        if (serializedBookmarks.isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_GAME_BOOKMARK_MIGRATED, true).apply()
            return
        }

        val gameUrl = UrlFormatter.formatNavigableUrl(GAME_BOOKMARK_URL)
        val targetClean = gameUrl.trimEnd('/')

        val entries = loadBookmarkEntries(context).toMutableList()
        if (entries.none { it.url.trimEnd('/').equals(targetClean, ignoreCase = true) }) {
            entries.add(BookmarkEntry(gameUrl, "Kododake Games"))
            persistBookmarkEntries(context, entries)
            cachedBookmarkEntries = entries
        }

        val slots = StartPagePreferences.loadStartPageSlots(context).toMutableList()
        val alreadyPinned = slots.any { it.trimEnd('/').equals(targetClean, ignoreCase = true) }
        if (!alreadyPinned) {
            val emptySlotIndex = slots.indexOfFirst { it.isBlank() }
            if (emptySlotIndex in 0 until MAX_START_PAGE_SITES) {
                slots[emptySlotIndex] = gameUrl
                StartPagePreferences.persistStartPageSlots(context, slots)
            }
        }

        prefs.edit().putBoolean(KEY_GAME_BOOKMARK_MIGRATED, true).apply()
    }

    fun addBookmark(context: Context, url: String, title: String = ""): Boolean {
        val navigable = UrlFormatter.formatNavigableUrl(url)
        val entries = getBookmarkEntries(context).toMutableList()
        if (entries.any { it.url.equals(navigable, ignoreCase = false) }) {
            return false
        }
        val cleanTitle = title.trim().ifBlank { BookmarkUrlFormatter.displayTitleForUrl(navigable) }
        entries.add(0, BookmarkEntry(navigable, cleanTitle))
        setBookmarkEntries(context, entries)
        return true
    }

    fun updateBookmarkTitle(context: Context, url: String, title: String): Boolean {
        if (title.isBlank()) return false
        val navigable = UrlFormatter.formatNavigableUrl(url)
        val cleanTarget = navigable.trimEnd('/')
        val entries = getBookmarkEntries(context).toMutableList()
        val index = entries.indexOfFirst {
            it.url.equals(navigable, ignoreCase = false) || it.url.trimEnd('/') == cleanTarget
        }
        if (index >= 0) {
            val newTitle = title.trim()
            if (entries[index].title == newTitle) return false
            entries[index] = entries[index].copy(title = newTitle)
            setBookmarkEntries(context, entries)
            return true
        }
        return false
    }

    fun removeBookmark(context: Context, url: String): Boolean {
        val navigable = UrlFormatter.formatNavigableUrl(url)
        val entries = getBookmarkEntries(context).toMutableList()
        val removed = entries.removeAll { it.url.equals(navigable, ignoreCase = false) || it.url == url }
        if (removed) {
            setBookmarkEntries(context, entries)
            val slotIndex = findStartPageSlot(context, url)
            if (slotIndex >= 0) {
                clearStartPageSlot(context, slotIndex)
            }
        }
        return removed
    }

    fun getStartPageSlots(context: Context): List<String?> = StartPagePreferences.getStartPageSlots(context)
    fun getStartPageSites(context: Context): List<String> = StartPagePreferences.getStartPageSites(context)
    fun findStartPageSlot(context: Context, url: String): Int = StartPagePreferences.findStartPageSlot(context, url)
    fun isStartPageSite(context: Context, url: String): Boolean = StartPagePreferences.isStartPageSite(context, url)
    fun setStartPageSlot(context: Context, index: Int, url: String?) = StartPagePreferences.setStartPageSlot(context, index, url)
    fun setStartPageSlots(context: Context, slots: List<String?>) = StartPagePreferences.setStartPageSlots(context, slots)
    fun clearStartPageSlot(context: Context, index: Int) = StartPagePreferences.clearStartPageSlot(context, index)
    fun getStartPageBackgroundUri(context: Context): String? = StartPagePreferences.getStartPageBackgroundUri(context)
    fun setStartPageBackgroundUri(context: Context, uri: String?) = StartPagePreferences.setStartPageBackgroundUri(context, uri)
    fun clearStartPageBackgroundUri(context: Context) = StartPagePreferences.clearStartPageBackgroundUri(context)

    private fun loadBookmarkEntries(context: Context): List<BookmarkEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index)
                    if (obj != null) {
                        val url = obj.optString("url").trim()
                        val title = obj.optString("title").trim()
                        if (url.isNotEmpty()) {
                            add(BookmarkEntry(url, title.ifBlank { BookmarkUrlFormatter.displayTitleForUrl(url) }))
                        }
                    } else {
                        val url = array.optString(index).trim()
                        if (url.isNotEmpty()) {
                            add(BookmarkEntry(url, BookmarkUrlFormatter.displayTitleForUrl(url)))
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistBookmarkEntries(context: Context, entries: List<BookmarkEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply { put("url", entry.url); put("title", entry.title) })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BOOKMARKS, array.toString()).apply()
    }
}
