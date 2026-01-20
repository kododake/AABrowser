package com.kododake.aabrowser.data

import android.content.Context
import android.net.Uri
import android.util.Patterns
import com.kododake.aabrowser.model.UserAgentProfile
import org.json.JSONArray

object BrowserPreferences {
    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_LAST_URL = "last_url"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_USER_AGENT_PROFILE = "user_agent_profile"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_ALLOWED_CLEAR_HOSTS = "allowed_clear_hosts"
    private const val DEFAULT_URL = "https://www.google.com"
    private const val SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"

    fun getUserAgentProfile(context: Context): UserAgentProfile {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_AGENT_PROFILE, null)
        return UserAgentProfile.fromKey(key)
    }

    fun setUserAgentProfile(context: Context, profile: UserAgentProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_AGENT_PROFILE, profile.storageKey)
            .apply()
    }

    fun resolveInitialUrl(context: Context, fallback: String = DEFAULT_URL): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_URL, null)
        if (stored.isNullOrBlank()) return fallback

        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return fallback
        val scheme = uri.scheme?.lowercase() ?: return fallback
        if (scheme == "http") {
            val host = uri.host?.lowercase() ?: return fallback
            if (!isHostAllowedCleartext(context, host)) return fallback
        }
        return stored
    }

    fun persistUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase() ?: return
        if (scheme == "about") return
        if (scheme != "http" && scheme != "https") return
        if (scheme == "http") {
            val host = uri.host?.lowercase() ?: return
            if (!isHostAllowedCleartext(context, host)) return
        }

        val normalized = trimmed
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

    fun getBookmarks(context: Context): List<String> {
        val bookmarks = loadBookmarks(context)
        if (bookmarks.isEmpty()) {
            val defaults = listOf("https://www.google.com", "https://www.youtube.com")
            persistBookmarks(context, defaults)
            return defaults
        }
        return bookmarks
    }

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

    fun isHostAllowedCleartext(context: Context, host: String?): Boolean {
        if (host == null) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_ALLOWED_CLEAR_HOSTS, null) ?: return false
        return runCatching {
            val array = JSONArray(serialized)
            for (i in 0 until array.length()) {
                if (array.optString(i).equals(host, ignoreCase = true)) return true
            }
            false
        }.getOrDefault(false)
    }

    fun addAllowedCleartextHost(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_ALLOWED_CLEAR_HOSTS, null)
        val list = runCatching {
            val arr = JSONArray(current)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }.toMutableList()
        }.getOrDefault(mutableListOf())
        if (list.any { it.equals(host, ignoreCase = true) }) return
        list.add(host)
        val out = JSONArray()
        list.forEach { out.put(it) }
        prefs.edit().putString(KEY_ALLOWED_CLEAR_HOSTS, out.toString()).apply()
    }

    

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
