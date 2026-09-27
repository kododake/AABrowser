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
import org.json.JSONArray

object StartPagePreferences {
    const val PREFS_NAME = "browser_prefs"
    const val MAX_START_PAGE_SITES = 6

    private const val KEY_START_PAGE_SLOTS = "start_page_slots"
    private const val KEY_START_PAGE_BACKGROUND_URI = "start_page_background_uri"

    @Volatile
    private var cachedStartPageSlots: List<String?>? = null

    fun getStartPageSlots(context: Context): List<String?> {
        val cached = cachedStartPageSlots
        if (cached != null) return cached

        val slots = loadStartPageSlots(context)
        val result = slots.map { it.ifBlank { null } }
        cachedStartPageSlots = result
        return result
    }

    fun getStartPageSites(context: Context): List<String> {
        return getStartPageSlots(context).filterNotNull()
    }

    fun findStartPageSlot(context: Context, url: String): Int {
        val normalized = UrlFormatter.formatNavigableUrl(url)
        return loadStartPageSlots(context).indexOfFirst { it == normalized }
    }

    fun isStartPageSite(context: Context, url: String): Boolean = findStartPageSlot(context, url) >= 0

    fun setStartPageSlot(context: Context, index: Int, url: String?) {
        if (index !in 0 until MAX_START_PAGE_SITES) return
        val slots = loadStartPageSlots(context).toMutableList()
        val normalized = url?.trim().orEmpty()
        if (normalized.isBlank()) {
            slots[index] = ""
            persistStartPageSlots(context, slots)
            cachedStartPageSlots = slots.map { it.ifBlank { null } }
            return
        }

        val navigable = UrlFormatter.formatNavigableUrl(normalized)
        if (!UrlFormatter.isHttpOrHttps(navigable)) return

        val existingIndex = slots.indexOfFirst { it == navigable }
        if (existingIndex >= 0) {
            slots[existingIndex] = ""
        }
        slots[index] = navigable
        persistStartPageSlots(context, slots)
        cachedStartPageSlots = slots.map { it.ifBlank { null } }
    }

    fun setStartPageSlots(context: Context, slots: List<String?>) {
        val normalized = MutableList(MAX_START_PAGE_SITES) { index ->
            val raw = slots.getOrNull(index)?.trim().orEmpty()
            if (raw.isNotBlank()) {
                val navigable = UrlFormatter.formatNavigableUrl(raw)
                if (UrlFormatter.isHttpOrHttps(navigable)) navigable else ""
            } else {
                ""
            }
        }
        persistStartPageSlots(context, normalized)
        cachedStartPageSlots = normalized.map { it.ifBlank { null } }
    }

    fun clearStartPageSlot(context: Context, index: Int) = setStartPageSlot(context, index, null)

    fun getStartPageBackgroundUri(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_START_PAGE_BACKGROUND_URI, null)?.takeIf { it.isNotBlank() }

    fun setStartPageBackgroundUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_START_PAGE_BACKGROUND_URI, uri?.takeIf { it.isNotBlank() }).apply()
    }

    fun clearStartPageBackgroundUri(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_START_PAGE_BACKGROUND_URI).apply()
    }

    private fun defaultSlots(): MutableList<String> = MutableList(MAX_START_PAGE_SITES) { index ->
        BookmarkPreferences.DEFAULT_BOOKMARKS.getOrElse(index) { "" }
    }

    internal fun loadStartPageSlots(context: Context): MutableList<String> {
        val cached = cachedStartPageSlots
        if (cached != null) return cached.map { it ?: "" }.toMutableList()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_START_PAGE_SLOTS, null)
        if (serialized.isNullOrBlank()) {
            val defaults = defaultSlots()
            persistStartPageSlots(context, defaults)
            cachedStartPageSlots = defaults.map { it.ifBlank { null } }
            return defaults
        }

        return runCatching {
            val array = JSONArray(serialized)
            val storedLength = array.length()
            val slots = MutableList(MAX_START_PAGE_SITES) { index ->
                if (index < storedLength) {
                    array.optString(index).trim().takeIf { UrlFormatter.isHttpOrHttps(it) }.orEmpty()
                } else {
                    BookmarkPreferences.DEFAULT_BOOKMARKS.getOrElse(index) { "" }
                }
            }
            if (storedLength < MAX_START_PAGE_SITES) persistStartPageSlots(context, slots)
            cachedStartPageSlots = slots.map { it.ifBlank { null } }
            slots
        }.getOrElse {
            val defaults = defaultSlots()
            cachedStartPageSlots = defaults.map { it.ifBlank { null } }
            defaults
        }
    }

    internal fun persistStartPageSlots(context: Context, slots: List<String>) {
        val normalizedSlots = MutableList(MAX_START_PAGE_SITES) { "" }
        slots.take(MAX_START_PAGE_SITES).forEachIndexed { index, value ->
            normalizedSlots[index] = value.trim().takeIf { UrlFormatter.isHttpOrHttps(it) }.orEmpty()
        }

        val array = JSONArray()
        normalizedSlots.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_START_PAGE_SLOTS, array.toString())
            .apply()
        cachedStartPageSlots = normalizedSlots.map { it.ifBlank { null } }
    }
}
