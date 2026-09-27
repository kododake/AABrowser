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

package com.kododake.aabrowser.tabs

import android.content.Context
import com.kododake.aabrowser.data.BrowserPreferences

object TabStateStore {

    fun persistTabSession(
        context: Context,
        browserTabs: List<BrowserTab>,
        activeTabId: Long?
    ) {
        val entries = browserTabs.map { tab ->
            BrowserPreferences.TabSessionEntry(
                url = tab.currentUrl.takeIf { u -> u.isNotBlank() },
                title = tab.currentTitle.takeIf { t -> t.isNotBlank() }
            )
        }
        val activeIndex = browserTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
        BrowserPreferences.persistTabSession(context, entries, activeIndex)
    }
}
