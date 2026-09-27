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
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences

object TabInitializer {

    fun initialize(
        context: Context,
        intentUrl: String?,
        homePageUrl: String?,
        lastVisitedUrl: String?,
        restoreTabsOnLaunch: Boolean,
        resumeLastPageOnLaunch: Boolean,
        shouldForceSessionRestore: Boolean,
        createTab: (url: String?, title: String, activate: Boolean) -> BrowserTab?,
        switchToTab: (tabId: Long) -> Unit,
        getTabIdAtIndex: (Int) -> Long?,
        getTabsCount: () -> Int
    ) {
        val shouldRestoreSavedTabs = shouldForceSessionRestore ||
            (intentUrl == null && homePageUrl.isNullOrBlank() && restoreTabsOnLaunch)

        val savedTabs = if (shouldRestoreSavedTabs) {
            BrowserPreferences.getSavedTabSession(context)
        } else {
            emptyList()
        }

        when {
            intentUrl != null -> {
                createTab(BrowserPreferences.formatNavigableUrl(intentUrl), "", true)
            }
            !homePageUrl.isNullOrBlank() -> {
                createTab(homePageUrl, "", true)
            }
            savedTabs.isNotEmpty() -> {
                savedTabs.forEach { entry ->
                    createTab(entry.url, entry.title.orEmpty(), false)
                }
                val savedActiveIndex = BrowserPreferences.getSavedActiveTabIndex(context)
                val targetIndex = savedActiveIndex.coerceIn(0, (getTabsCount() - 1).coerceAtLeast(0))
                val targetId = getTabIdAtIndex(targetIndex)
                if (targetId != null) {
                    switchToTab(targetId)
                }
            }
            resumeLastPageOnLaunch && !lastVisitedUrl.isNullOrBlank() -> {
                createTab(lastVisitedUrl, "", true)
            }
            else -> {
                createTab(null, context.getString(R.string.tab_manager_blank_title), true)
            }
        }
    }
}
