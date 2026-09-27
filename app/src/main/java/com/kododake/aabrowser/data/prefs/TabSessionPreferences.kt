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
import org.json.JSONObject

object TabSessionPreferences {
    const val PREFS_NAME = "browser_prefs"
    const val MAX_OPEN_TABS = 8

    private const val KEY_LAST_URL = "last_url"
    private const val KEY_RESUME_LAST_PAGE_ON_LAUNCH = "resume_last_page_on_launch"
    private const val KEY_RESTORE_TABS_ON_LAUNCH = "restore_tabs_on_launch"
    private const val KEY_TAB_SESSION = "tab_session"
    private const val KEY_ACTIVE_TAB_INDEX = "active_tab_index"
    private const val KEY_HOME_PAGE_URL = "home_page_url"

    data class TabSessionEntry(
        val url: String?,
        val title: String?
    )

    fun getLastVisitedUrl(context: Context): String? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_URL, null)
            ?.trim()
        return raw?.takeIf { UrlFormatter.isHttpOrHttps(it) }
    }

    fun resolveInitialUrl(context: Context, fallback: String = UrlFormatter.DEFAULT_URL): String {
        return getLastVisitedUrl(context) ?: fallback
    }

    fun persistUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (!UrlFormatter.isHttpOrHttps(trimmed)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_URL, trimmed)
            .apply()
    }

    fun shouldResumeLastPageOnLaunch(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESUME_LAST_PAGE_ON_LAUNCH, false)
    }

    fun setResumeLastPageOnLaunch(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESUME_LAST_PAGE_ON_LAUNCH, enabled)
            .apply()
    }

    fun shouldRestoreTabsOnLaunch(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESTORE_TABS_ON_LAUNCH, true)
    }

    fun setRestoreTabsOnLaunch(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTORE_TABS_ON_LAUNCH, enabled)
            .apply()
    }

    fun getSavedTabSession(context: Context): List<TabSessionEntry> {
        val serialized = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TAB_SESSION, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val rawUrl = entry.optString("url").trim()
                    val normalizedUrl = rawUrl.takeIf { it.isEmpty() || UrlFormatter.isHttpOrHttps(it) }
                    val title = entry.optString("title").trim().takeIf { it.isNotEmpty() }
                    if (normalizedUrl != null) {
                        add(TabSessionEntry(url = normalizedUrl.ifEmpty { null }, title = title))
                    }
                }
            }.take(MAX_OPEN_TABS)
        }.getOrDefault(emptyList())
    }

    fun getSavedActiveTabIndex(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACTIVE_TAB_INDEX, 0)
            .coerceAtLeast(0)
    }

    fun persistTabSession(
        context: Context,
        tabs: List<TabSessionEntry>,
        activeIndex: Int
    ) {
        val normalizedTabs = buildList {
            tabs.take(MAX_OPEN_TABS).forEach { tab ->
                val normalizedUrl = tab.url
                    ?.trim()
                    ?.takeIf { UrlFormatter.isHttpOrHttps(it) }
                if (normalizedUrl != null || tab.url.isNullOrBlank()) {
                    add(
                        TabSessionEntry(
                            url = normalizedUrl,
                            title = tab.title?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        }

        val array = JSONArray()
        normalizedTabs.forEach { tab ->
            array.put(
                JSONObject().apply {
                    put("url", tab.url.orEmpty())
                    put("title", tab.title.orEmpty())
                }
            )
        }

        val normalizedActiveIndex = if (normalizedTabs.isEmpty()) {
            0
        } else {
            activeIndex.coerceIn(0, normalizedTabs.lastIndex)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAB_SESSION, array.toString())
            .putInt(KEY_ACTIVE_TAB_INDEX, normalizedActiveIndex)
            .apply()
    }

    fun clearSavedTabSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TAB_SESSION)
            .remove(KEY_ACTIVE_TAB_INDEX)
            .apply()
    }

    fun getHomePageUrl(context: Context): String? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOME_PAGE_URL, null)
            ?.trim()
        if (raw.isNullOrEmpty()) return null
        return raw.takeIf { UrlFormatter.isHttpOrHttps(it) }
    }

    fun setHomePageUrl(context: Context, url: String?) {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            clearHomePageUrl(context)
            return
        }
        val formatted = UrlFormatter.formatNavigableUrl(trimmed)
        if (!UrlFormatter.isHttpOrHttps(formatted)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME_PAGE_URL, formatted)
            .apply()
    }

    fun clearHomePageUrl(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HOME_PAGE_URL)
            .apply()
    }
}
