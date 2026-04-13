package com.kododake.aabrowser.data

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Patterns
import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.model.UserAgentProfile
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

object BrowserPreferences {
    data class TabSessionEntry(
        val url: String?,
        val title: String?
    )

    private const val PREFS_NAME = "browser_prefs"
    private const val KEY_LAST_URL = "last_url"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_USER_AGENT_PROFILE = "user_agent_profile"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_ALLOWED_CLEAR_HOSTS = "allowed_clear_hosts"
    private const val KEY_ALLOWED_MICROPHONE_HOSTS = "allowed_microphone_hosts"
    private const val KEY_GLOBAL_SCALE_PERCENT = "global_scale_percent"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BETA_FORCE_DARK_PAGES = "beta_force_dark_pages"
    private const val KEY_RESUME_LAST_PAGE_ON_LAUNCH = "resume_last_page_on_launch"
    private const val KEY_RESTORE_TABS_ON_LAUNCH = "restore_tabs_on_launch"
    private const val KEY_ALWAYS_SHOW_URL_BAR = "always_show_url_bar"
    private const val KEY_QUICK_ACTION_BUTTON_MODE = "quick_action_button_mode"
    private const val KEY_QUICK_ACTION_BUTTON_ALWAYS_VISIBLE = "quick_action_button_always_visible"
    private const val KEY_QUICK_ACTION_BUTTON_POSITION = "quick_action_button_position"
    private const val KEY_TAB_SESSION = "tab_session"
    private const val KEY_ACTIVE_TAB_INDEX = "active_tab_index"
    private const val KEY_START_PAGE_SLOTS = "start_page_slots"
    private const val KEY_START_PAGE_BACKGROUND_URI = "start_page_background_uri"
    private const val KEY_HOME_PAGE_URL = "home_page_url"
    private const val DEFAULT_URL = "https://www.google.com"
    private const val SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"

    private val DEFAULT_BOOKMARKS = listOf(
        "https://www.google.com",
        "https://youtube.com",
        "https://www.twitch.tv",
        "https://kick.com",
        "https://weather.com",
        "https://keepandroidopen.org"
    )

    const val MAX_START_PAGE_SITES = 6
    const val MAX_OPEN_TABS = 8
    const val MIN_GLOBAL_SCALE_PERCENT = 60
    const val MAX_GLOBAL_SCALE_PERCENT = 200
    const val DEFAULT_GLOBAL_SCALE_PERCENT = 100

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
        return getLastVisitedUrl(context) ?: fallback
    }

    fun getLastVisitedUrl(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_URL, null)
        if (stored.isNullOrBlank()) return null

        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme == "http") {
            val host = uri.host?.lowercase() ?: return null
            if (!isHostAllowedCleartext(context, host)) return null
        }
        return if (scheme == "http" || scheme == "https") stored else null
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

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_URL, trimmed)
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

    fun getThemeMode(context: Context): AppThemeMode {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        return AppThemeMode.fromKey(key)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageKey)
            .apply()
    }

    fun isBetaForceDarkPagesEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BETA_FORCE_DARK_PAGES, false)
    }

    fun setBetaForceDarkPagesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BETA_FORCE_DARK_PAGES, enabled)
            .apply()
    }

    fun shouldAlwaysShowUrlBar(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALWAYS_SHOW_URL_BAR, false)
    }

    fun setAlwaysShowUrlBar(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALWAYS_SHOW_URL_BAR, enabled)
            .apply()
    }

    fun getQuickActionButtonMode(context: Context): QuickActionButtonMode {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUICK_ACTION_BUTTON_MODE, null)
        return QuickActionButtonMode.fromKey(key)
    }

    fun setQuickActionButtonMode(context: Context, mode: QuickActionButtonMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_ACTION_BUTTON_MODE, mode.storageKey)
            .apply()
    }

    fun isQuickActionButtonAlwaysVisible(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QUICK_ACTION_BUTTON_ALWAYS_VISIBLE, false)
    }

    fun setQuickActionButtonAlwaysVisible(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_QUICK_ACTION_BUTTON_ALWAYS_VISIBLE, enabled)
            .apply()
    }

    fun getQuickActionButtonPosition(context: Context): QuickActionButtonPosition {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUICK_ACTION_BUTTON_POSITION, null)
        return QuickActionButtonPosition.fromKey(key)
    }

    fun setQuickActionButtonPosition(context: Context, position: QuickActionButtonPosition) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_ACTION_BUTTON_POSITION, position.storageKey)
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
            .getBoolean(KEY_RESTORE_TABS_ON_LAUNCH, false)
    }

    fun setRestoreTabsOnLaunch(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTORE_TABS_ON_LAUNCH, enabled)
            .apply()
    }

    fun getSavedTabSession(context: Context): List<TabSessionEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_TAB_SESSION, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val rawUrl = entry.optString("url").trim()
                    val normalizedUrl = rawUrl.takeIf { it.isEmpty() || isHttpOrHttps(it) }
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
                    ?.takeIf { isHttpOrHttps(it) }
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

    fun getGlobalScalePercent(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_GLOBAL_SCALE_PERCENT, DEFAULT_GLOBAL_SCALE_PERCENT)
        return sanitizeGlobalScalePercent(stored)
    }

    fun sanitizeGlobalScalePercent(percent: Int): Int {
        return percent.coerceIn(MIN_GLOBAL_SCALE_PERCENT, MAX_GLOBAL_SCALE_PERCENT)
    }

    fun setGlobalScalePercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GLOBAL_SCALE_PERCENT, sanitizeGlobalScalePercent(percent))
            .apply()
    }

    fun createScaledContext(base: Context): Context {
        val scalePercent = getGlobalScalePercent(base)
        if (scalePercent == DEFAULT_GLOBAL_SCALE_PERCENT) return base

        val configuration = Configuration(base.resources.configuration)
        val stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE
            .takeIf { it > 0 }
            ?: configuration.densityDpi.takeIf { it > 0 }
            ?: base.resources.displayMetrics.densityDpi

        configuration.densityDpi = (stableDensity * (scalePercent / 100f)).roundToInt()
            .coerceAtLeast(1)
        return base.createConfigurationContext(configuration)
    }

    fun getHomePageUrl(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOME_PAGE_URL, null)
            ?.trim()
            .orEmpty()
        if (stored.isBlank()) return null

        val normalized = formatNavigableUrl(stored)
        return normalized.takeIf { isHttpOrHttps(it) }
    }

    fun setHomePageUrl(context: Context, url: String?) {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) {
            clearHomePageUrl(context)
            return
        }

        val normalized = formatNavigableUrl(value)
        if (!isHttpOrHttps(normalized)) return

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME_PAGE_URL, normalized)
            .apply()
    }

    fun clearHomePageUrl(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HOME_PAGE_URL)
            .apply()
    }

    fun getBookmarks(context: Context): List<String> {
        val bookmarks = loadBookmarks(context)
        if (bookmarks.isEmpty()) {
            persistBookmarks(context, DEFAULT_BOOKMARKS)
            return DEFAULT_BOOKMARKS
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
            val slotIndex = findStartPageSlot(context, url)
            if (slotIndex >= 0) {
                clearStartPageSlot(context, slotIndex)
            }
        }
        return removed
    }

    fun getStartPageSlots(context: Context): List<String?> {
        val slots = loadStartPageSlots(context)
        return slots.map { it.ifBlank { null } }
    }

    fun getStartPageSites(context: Context): List<String> {
        return getStartPageSlots(context).filterNotNull()
    }

    fun findStartPageSlot(context: Context, url: String): Int {
        val normalized = formatNavigableUrl(url)
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
            return
        }

        val navigable = formatNavigableUrl(normalized)
        if (!isHttpOrHttps(navigable)) return

        val existingIndex = slots.indexOfFirst { it == navigable }
        if (existingIndex >= 0) {
            slots[existingIndex] = ""
        }
        slots[index] = navigable
        persistStartPageSlots(context, slots)
    }

    fun clearStartPageSlot(context: Context, index: Int) {
        setStartPageSlot(context, index, null)
    }

    fun getStartPageBackgroundUri(context: Context): String? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_START_PAGE_BACKGROUND_URI, null)
        return value?.takeIf { it.isNotBlank() }
    }

    fun setStartPageBackgroundUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_START_PAGE_BACKGROUND_URI, uri?.takeIf { it.isNotBlank() })
            .apply()
    }

    fun clearStartPageBackgroundUri(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_START_PAGE_BACKGROUND_URI)
            .apply()
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
        return isHostAllowed(context, KEY_ALLOWED_CLEAR_HOSTS, host)
    }

    fun addAllowedCleartextHost(context: Context, host: String) {
        addAllowedHost(context, KEY_ALLOWED_CLEAR_HOSTS, host)
    }

    fun isHostAllowedMicrophone(context: Context, host: String?): Boolean {
        return isHostAllowed(context, KEY_ALLOWED_MICROPHONE_HOSTS, host)
    }

    fun addAllowedMicrophoneHost(context: Context, host: String) {
        addAllowedHost(context, KEY_ALLOWED_MICROPHONE_HOSTS, host)
    }

    fun clearSavedSitePermissions(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALLOWED_CLEAR_HOSTS)
            .remove(KEY_ALLOWED_MICROPHONE_HOSTS)
            .apply()
    }

    private fun isHostAllowed(context: Context, key: String, host: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalizedHost = host?.trim()?.lowercase()
        if (normalizedHost.isNullOrEmpty()) return false
        val serialized = prefs.getString(key, null) ?: return false
        return runCatching {
            val array = JSONArray(serialized)
            for (i in 0 until array.length()) {
                if (array.optString(i).equals(normalizedHost, ignoreCase = true)) return true
            }
            false
        }.getOrDefault(false)
    }

    private fun addAllowedHost(context: Context, key: String, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalizedHost = host.trim().lowercase()
        if (normalizedHost.isEmpty()) return
        val current = prefs.getString(key, null)
        val list = runCatching {
            val arr = JSONArray(current)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }.toMutableList()
        }.getOrDefault(mutableListOf())
        if (list.any { it.equals(normalizedHost, ignoreCase = true) }) return
        list.add(normalizedHost)
        val out = JSONArray()
        list.forEach { out.put(it) }
        prefs.edit().putString(key, out.toString()).apply()
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

    private fun loadStartPageSlots(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_START_PAGE_SLOTS, null)
        if (serialized.isNullOrBlank()) {
            val defaults = MutableList(MAX_START_PAGE_SITES) { index ->
                DEFAULT_BOOKMARKS.getOrElse(index) { "" }
            }
            persistStartPageSlots(context, defaults)
            return defaults
        }

        return runCatching {
            val array = JSONArray(serialized)
            val storedLength = array.length()
            val slots = MutableList(MAX_START_PAGE_SITES) { index ->
                if (index < storedLength) {
                    array.optString(index).trim().takeIf { isHttpOrHttps(it) }.orEmpty()
                } else {
                    DEFAULT_BOOKMARKS.getOrElse(index) { "" }
                }
            }
            if (storedLength < MAX_START_PAGE_SITES) {
                persistStartPageSlots(context, slots)
            }
            slots
        }.getOrElse {
            MutableList(MAX_START_PAGE_SITES) { index ->
                DEFAULT_BOOKMARKS.getOrElse(index) { "" }
            }
        }
    }

    private fun persistStartPageSlots(context: Context, slots: List<String>) {
        val normalizedSlots = MutableList(MAX_START_PAGE_SITES) { "" }
        slots.take(MAX_START_PAGE_SITES).forEachIndexed { index, value ->
            normalizedSlots[index] = value.trim().takeIf { isHttpOrHttps(it) }.orEmpty()
        }

        val array = JSONArray()
        normalizedSlots.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_START_PAGE_SLOTS, array.toString())
            .apply()
    }

    private fun isHttpOrHttps(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }
}
