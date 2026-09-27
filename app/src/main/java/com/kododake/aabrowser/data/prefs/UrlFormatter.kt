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

import android.net.Uri

object UrlFormatter {
    const val DEFAULT_URL = "https://www.google.com"
    private const val SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"

    fun formatNavigableUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (isHttpOrHttps(trimmed)) return trimmed
        if (trimmed.contains(" ") || !trimmed.contains(".")) {
            return toSearchUrl(trimmed)
        }
        val candidate = "https://$trimmed"
        return runCatching {
            val uri = Uri.parse(candidate)
            if (uri.host?.contains(".") == true) candidate else toSearchUrl(trimmed)
        }.getOrDefault(toSearchUrl(trimmed))
    }

    fun toSearchUrl(query: String): String = SEARCH_TEMPLATE.format(Uri.encode(query))

    fun defaultUrl(): String = DEFAULT_URL

    fun isHttpOrHttps(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
