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

package com.kododake.aabrowser.data

import android.content.Context
import com.kododake.aabrowser.data.prefs.BookmarkPreferences
import com.kododake.aabrowser.data.prefs.DisplayPreferences
import com.kododake.aabrowser.data.prefs.SitePermissionPreferences
import com.kododake.aabrowser.data.prefs.TabSessionPreferences
import com.kododake.aabrowser.data.prefs.UIPreferences
import com.kododake.aabrowser.data.prefs.UrlFormatter
import com.kododake.aabrowser.data.prefs.WebPreferences
import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.ui.compose.screens.bookmarks.BookmarkEntry

object BrowserPreferences {
    typealias TabSessionEntry = TabSessionPreferences.TabSessionEntry

    const val MAX_START_PAGE_SITES = BookmarkPreferences.MAX_START_PAGE_SITES
    const val MAX_OPEN_TABS = TabSessionPreferences.MAX_OPEN_TABS
    const val MIN_GLOBAL_SCALE_PERCENT = DisplayPreferences.MIN_GLOBAL_SCALE_PERCENT
    const val MAX_GLOBAL_SCALE_PERCENT = DisplayPreferences.MAX_GLOBAL_SCALE_PERCENT
    const val DEFAULT_GLOBAL_SCALE_PERCENT = DisplayPreferences.DEFAULT_GLOBAL_SCALE_PERCENT

    // --- URL Formatter ---
    fun formatNavigableUrl(raw: String): String = UrlFormatter.formatNavigableUrl(raw)
    fun toSearchUrl(query: String): String = UrlFormatter.toSearchUrl(query)
    fun defaultUrl(): String = UrlFormatter.defaultUrl()
    fun isHttpOrHttps(url: String): Boolean = UrlFormatter.isHttpOrHttps(url)

    // --- User Agent & Desktop Mode ---
    fun getUserAgentProfile(context: Context): UserAgentProfile = UIPreferences.getUserAgentProfile(context)
    fun setUserAgentProfile(context: Context, profile: UserAgentProfile) = UIPreferences.setUserAgentProfile(context, profile)
    fun getCustomUserAgent(context: Context): String = UIPreferences.getCustomUserAgent(context)
    fun setCustomUserAgent(context: Context, userAgent: String) = UIPreferences.setCustomUserAgent(context, userAgent)
    fun shouldUseDesktopMode(context: Context): Boolean = UIPreferences.shouldUseDesktopMode(context)
    fun toggleDesktopMode(context: Context): Boolean = UIPreferences.toggleDesktopMode(context)
    fun setDesktopMode(context: Context, enabled: Boolean) = UIPreferences.setDesktopMode(context, enabled)
    fun shouldUseFullscreenMode(context: Context): Boolean = UIPreferences.shouldUseFullscreenMode(context)
    fun setFullscreenMode(context: Context, enabled: Boolean) = UIPreferences.setFullscreenMode(context, enabled)

    // --- Navigation & Last Visited ---
    fun resolveInitialUrl(context: Context, fallback: String = UrlFormatter.DEFAULT_URL): String = TabSessionPreferences.resolveInitialUrl(context, fallback)
    fun getLastVisitedUrl(context: Context): String? = TabSessionPreferences.getLastVisitedUrl(context)
    fun persistUrl(context: Context, url: String) = TabSessionPreferences.persistUrl(context, url)
    fun shouldResumeLastPageOnLaunch(context: Context): Boolean = TabSessionPreferences.shouldResumeLastPageOnLaunch(context)
    fun setResumeLastPageOnLaunch(context: Context, enabled: Boolean) = TabSessionPreferences.setResumeLastPageOnLaunch(context, enabled)
    fun shouldRestoreTabsOnLaunch(context: Context): Boolean = TabSessionPreferences.shouldRestoreTabsOnLaunch(context)
    fun setRestoreTabsOnLaunch(context: Context, enabled: Boolean) = TabSessionPreferences.setRestoreTabsOnLaunch(context, enabled)

    // --- Theme & Display Scale ---
    fun getThemeMode(context: Context): AppThemeMode = DisplayPreferences.getThemeMode(context)
    fun setThemeMode(context: Context, mode: AppThemeMode) = DisplayPreferences.setThemeMode(context, mode)
    fun getGlobalScalePercent(context: Context): Int = DisplayPreferences.getGlobalScalePercent(context)
    fun sanitizeGlobalScalePercent(percent: Int): Int = DisplayPreferences.sanitizeGlobalScalePercent(percent)
    fun setGlobalScalePercent(context: Context, percent: Int) = DisplayPreferences.setGlobalScalePercent(context, percent)
    fun createScaledContext(base: Context): Context = DisplayPreferences.createScaledContext(base)

    // --- UI Controls ---
    fun getQuickActionButtonMode(context: Context): QuickActionButtonMode = UIPreferences.getQuickActionButtonMode(context)
    fun setQuickActionButtonMode(context: Context, mode: QuickActionButtonMode) = UIPreferences.setQuickActionButtonMode(context, mode)
    fun isQuickActionButtonAlwaysVisible(context: Context): Boolean = UIPreferences.isQuickActionButtonAlwaysVisible(context)
    fun setQuickActionButtonAlwaysVisible(context: Context, enabled: Boolean) = UIPreferences.setQuickActionButtonAlwaysVisible(context, enabled)
    fun getQuickActionButtonPosition(context: Context): QuickActionButtonPosition = UIPreferences.getQuickActionButtonPosition(context)
    fun setQuickActionButtonPosition(context: Context, position: QuickActionButtonPosition) = UIPreferences.setQuickActionButtonPosition(context, position)
    fun shouldHideSponsors(context: Context): Boolean = UIPreferences.shouldHideSponsors(context)
    fun setHideSponsors(context: Context, hide: Boolean) = UIPreferences.setHideSponsors(context, hide)

    // --- Tab Session ---
    fun getSavedTabSession(context: Context): List<TabSessionEntry> = TabSessionPreferences.getSavedTabSession(context)
    fun getSavedActiveTabIndex(context: Context): Int = TabSessionPreferences.getSavedActiveTabIndex(context)
    fun persistTabSession(context: Context, tabs: List<TabSessionEntry>, activeIndex: Int) = TabSessionPreferences.persistTabSession(context, tabs, activeIndex)
    fun clearSavedTabSession(context: Context) = TabSessionPreferences.clearSavedTabSession(context)

    // --- Home Page ---
    fun getHomePageUrl(context: Context): String? = TabSessionPreferences.getHomePageUrl(context)
    fun setHomePageUrl(context: Context, url: String?) = TabSessionPreferences.setHomePageUrl(context, url)
    fun clearHomePageUrl(context: Context) = TabSessionPreferences.clearHomePageUrl(context)

    // --- Bookmarks & Start Page Slots ---
    fun getBookmarkEntries(context: Context): List<BookmarkEntry> = BookmarkPreferences.getBookmarkEntries(context)
    fun getBookmarks(context: Context): List<String> = BookmarkPreferences.getBookmarks(context)
    fun setBookmarkEntries(context: Context, entries: List<BookmarkEntry>) = BookmarkPreferences.setBookmarkEntries(context, entries)
    fun setBookmarks(context: Context, bookmarks: List<String>) = BookmarkPreferences.setBookmarks(context, bookmarks)
    fun addBookmark(context: Context, url: String, title: String = ""): Boolean = BookmarkPreferences.addBookmark(context, url, title)
    fun updateBookmarkTitle(context: Context, url: String, title: String): Boolean = BookmarkPreferences.updateBookmarkTitle(context, url, title)
    fun removeBookmark(context: Context, url: String): Boolean = BookmarkPreferences.removeBookmark(context, url)
    fun getStartPageSlots(context: Context): List<String?> = BookmarkPreferences.getStartPageSlots(context)
    fun getStartPageSites(context: Context): List<String> = BookmarkPreferences.getStartPageSites(context)
    fun findStartPageSlot(context: Context, url: String): Int = BookmarkPreferences.findStartPageSlot(context, url)
    fun isStartPageSite(context: Context, url: String): Boolean = BookmarkPreferences.isStartPageSite(context, url)
    fun setStartPageSlot(context: Context, index: Int, url: String?) = BookmarkPreferences.setStartPageSlot(context, index, url)
    fun setStartPageSlots(context: Context, slots: List<String?>) = BookmarkPreferences.setStartPageSlots(context, slots)
    fun clearStartPageSlot(context: Context, index: Int) = BookmarkPreferences.clearStartPageSlot(context, index)
    fun getStartPageBackgroundUri(context: Context): String? = BookmarkPreferences.getStartPageBackgroundUri(context)
    fun setStartPageBackgroundUri(context: Context, uri: String?) = BookmarkPreferences.setStartPageBackgroundUri(context, uri)
    fun clearStartPageBackgroundUri(context: Context) = BookmarkPreferences.clearStartPageBackgroundUri(context)
    fun ensureGameBookmarkMigrated(context: Context) = BookmarkPreferences.ensureGameBookmarkMigrated(context)

    // --- Site Permissions & Host Exceptions ---
    fun isHostAllowedCleartext(context: Context, host: String?): Boolean = SitePermissionPreferences.isHostAllowedCleartext(context, host)
    fun addAllowedCleartextHost(context: Context, host: String) = SitePermissionPreferences.addAllowedCleartextHost(context, host)
    fun clearAllowedCleartextHosts(context: Context) = SitePermissionPreferences.clearAllowedCleartextHosts(context)
    fun isHostAllowedLocation(context: Context, host: String?): Boolean = SitePermissionPreferences.isHostAllowedLocation(context, host)
    fun addAllowedLocationHost(context: Context, host: String) = SitePermissionPreferences.addAllowedLocationHost(context, host)
    fun isHostAllowedMicrophone(context: Context, host: String?): Boolean = SitePermissionPreferences.isHostAllowedMicrophone(context, host)
    fun addAllowedMicrophoneHost(context: Context, host: String) = SitePermissionPreferences.addAllowedMicrophoneHost(context, host)
    fun clearSavedSitePermissions(context: Context) = SitePermissionPreferences.clearSavedSitePermissions(context)

    // --- Web Engine & DRM ---
    fun isDrmL3EnforcerEnabled(context: Context): Boolean = WebPreferences.isDrmL3EnforcerEnabled(context)
    fun setDrmL3EnforcerEnabled(context: Context, enabled: Boolean) = WebPreferences.setDrmL3EnforcerEnabled(context, enabled)
}
