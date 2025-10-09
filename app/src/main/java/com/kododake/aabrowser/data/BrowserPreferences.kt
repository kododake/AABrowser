package com.kododake.aabrowser.data

import android.content.Context
import android.net.Uri
import android.util.Patterns
import org.json.JSONArray

object BrowserPreferences {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_LAST_URL = "last_url"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val DEFAULT_URL = "https://www.google.com"
    private const val SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"

    fun resolveInitialUrl(context: Context, fallback: String = DEFAULT_URL): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_URL, null)
        return stored?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun persistUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val scheme = runCatching { Uri.parse(trimmed).scheme }.getOrNull()?.lowercase()
        if (scheme == "about") return
        val normalized = if (scheme == "http" || scheme == "https") {
            trimmed
        } else {
            formatNavigableUrl(trimmed)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_URL, normalized)
            .apply()
    }

    fun shouldUseDesktopMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESKTOP_MODE, false)
    }

    fun toggleDesktopMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useDesktop = !prefs.getBoolean(KEY_DESKTOP_MODE, false)
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, useDesktop).apply()
        return useDesktop
    }

    fun setDesktopMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESKTOP_MODE, enabled)
            .apply()
    }

    fun getBookmarks(context: Context): List<String> = loadBookmarks(context)

    fun addBookmark(context: Context, url: String): Boolean {
        val navigable = formatNavigableUrl(url)
        val bookmarks = loadBookmarks(context)
        if (bookmarks.any { it.equals(navigable, ignoreCase = false) }) {
            return false
        }
        val updated = mutableListOf(navigable)
        updated.addAll(bookmarks)
        persistBookmarks(context, updated)
        return true
    }

    fun removeBookmark(context: Context, url: String): Boolean {
        val bookmarks = loadBookmarks(context).toMutableList()
        val removed = bookmarks.remove(url)
        if (removed) {
            persistBookmarks(context, bookmarks)
        }
        return removed
    }

    fun formatNavigableUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DEFAULT_URL
        val lower = trimmed.lowercase()
        val hasProtocol = lower.startsWith("http://") || lower.startsWith("https://")
        val candidate = if (hasProtocol) trimmed else "https://$trimmed"
        return if (Patterns.WEB_URL.matcher(candidate).matches()) {
            candidate
        } else {
            toSearchUrl(trimmed)
        }
    }

    fun toSearchUrl(query: String): String = SEARCH_TEMPLATE.format(Uri.encode(query))

    fun defaultUrl(): String = DEFAULT_URL

    private fun loadBookmarks(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistBookmarks(context: Context, bookmarks: List<String>) {
        val array = JSONArray()
        bookmarks.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKMARKS, array.toString())
            .apply()
    }
}
