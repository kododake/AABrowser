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

import android.net.Uri
import java.net.URI

object BookmarkUrlFormatter {

    fun isActiveWebsiteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    fun displayLabelForUrl(url: String): String {
        return try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }

    fun displayTitleForUrl(url: String): String {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isBlank()) {
            return displayLabelForUrl(url)
        }

        val cleanedHost = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

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
            else -> null
        }
        if (mapped != null) {
            return mapped
        }

        val segment = mainDomain.split('-', '_').firstOrNull().orEmpty()
        if (segment.isBlank()) {
            return displayLabelForUrl(url)
        }
        return segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
