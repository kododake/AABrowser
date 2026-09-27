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

package com.kododake.aabrowser.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.compose.ui.graphics.toArgb
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.controllers.FullscreenController
import com.kododake.aabrowser.ui.controllers.MenuDragGestureHelper

class BrowserUIManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val bookmarkManager: BookmarkManager,
    private val startPageManager: StartPageManager,
    private val callbacks: UICallbacks
) {

    interface UICallbacks {
        fun onNavigateToAddress(raw: String, closeMenuAfterNavigate: Boolean)
        fun onShowQrCodeView()
        fun onShowCheckLatestView()
        fun onShowSettingsView()
        fun handleQuickActionButtonPressed()
        fun resolveThemeColor(attrRes: Int): Int
        fun showMenuButtonTemporarily()
    }

    private val fullscreenController = FullscreenController(
        activity = activity,
        binding = binding,
        onFullscreenChanged = { }
    )

    val menuHelper = com.kododake.aabrowser.ui.controllers.MenuSetupHelper()

    fun isInFullscreen(): Boolean = fullscreenController.isInFullscreen()
    fun isImmersiveMode(): Boolean = fullscreenController.isImmersiveMode()
    fun setImmersiveMode(enabled: Boolean) = fullscreenController.setImmersiveMode(enabled)

    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenController.enterFullscreen(
            view = view,
            callback = callback,
            onPrepareEnter = {
                if (binding.menuOverlay.isVisible) hideMenuOverlay()
                menuHelper.hideFab()
                tabManager.activeTab?.webView?.visibility = View.INVISIBLE
            }
        )
    }

    fun exitFullscreen(fromWebChrome: Boolean = false) {
        fullscreenController.exitFullscreen(
            fromWebChrome = fromWebChrome,
            onRestoreView = {
                val webViewVisibility = if (startPageManager.isShowingStartPage) View.INVISIBLE else View.VISIBLE
                tabManager.activeTab?.webView?.visibility = webViewVisibility
                applyQuickActionButtonPreferences()
                callbacks.showMenuButtonTemporarily()
            }
        )
    }

    fun applyQuickActionButtonPreferences() {
        val mode = BrowserPreferences.getQuickActionButtonMode(activity)
        menuHelper.setFabMode(mode == QuickActionButtonMode.ADDRESS_BAR)
        val density = activity.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val position = BrowserPreferences.getQuickActionButtonPosition(activity)
        val layoutParams = binding.fabComposeView.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.gravity = when (position) {
            QuickActionButtonPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            QuickActionButtonPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
            QuickActionButtonPosition.TOP_LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            QuickActionButtonPosition.TOP_RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
        }
        val isTop = position == QuickActionButtonPosition.TOP_LEFT || position == QuickActionButtonPosition.TOP_RIGHT
        layoutParams.setMargins(margin, margin, margin, margin)
        binding.fabComposeView.layoutParams = layoutParams

        val alwaysVisible = BrowserPreferences.isQuickActionButtonAlwaysVisible(activity)
        if ((startPageManager.isShowingStartPage || alwaysVisible) && !isInFullscreen() && !binding.menuOverlay.isVisible) {
            menuHelper.showFab()
        }
    }

    fun showMenuOverlay(focusAddressBar: Boolean = false) {
        val isStart = startPageManager.isShowingStartPage
        val curTab = tabManager.activeTab
        menuHelper.updatePage(
            url = if (isStart) "" else (curTab?.currentUrl.orEmpty()),
            title = if (isStart) "" else (curTab?.currentTitle.orEmpty())
        )
        binding.menuOverlay.visibility = View.VISIBLE
        binding.menuComposeView.visibility = View.VISIBLE
        applyFrostedGlassProgress(if (menuHelper.stateHolder.isReturningFromSubscreen) 1f else 0f)
        menuHelper.showMenu()
        menuHelper.hideFab()

        bookmarkManager.refreshBookmarks()
        tabManager.refreshTabs()
        startPageManager.refreshStartPage()
        refreshShieldsSiteState()
    }

    /** Reflects the global Shields switch and the active tab's per-site state on the menu card. */
    fun refreshShieldsSiteState() {
        val host = shieldsSiteHost()
        menuHelper.stateHolder.updateShieldsState(
            globallyEnabled = BrowserPreferences.isShieldsEnabled(activity),
            host = host,
            onForSite = host != null && !BrowserPreferences.isShieldsDisabledForHost(activity, host)
        )
    }

    /** Host of the active tab's page when it is a web page, otherwise null. */
    fun shieldsSiteHost(): String? {
        val url = tabManager.activeTab?.webView?.url ?: tabManager.activeTab?.currentUrl
        val uri = url?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        return uri.host?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    fun hasActiveSubScreen(): Boolean = binding.settingsComposeView.isVisible ||
        binding.bookmarkComposeView.isVisible ||
        binding.tabComposeView.isVisible ||
        binding.versionComposeView.isVisible ||
        binding.qrCodeComposeView.isVisible

    private var lastDarkState: Boolean? = null
    private var cachedTintedColor: Int = 0
    private var lastAppliedBlur: Float = -1f

    private fun getTintedMonetColor(isDark: Boolean): Int {
        if (lastDarkState == isDark && cachedTintedColor != 0) return cachedTintedColor
        val (surfaceColor, primaryColor) = resolveMonetThemeColors(isDark)
        lastDarkState = isDark
        cachedTintedColor = androidx.core.graphics.ColorUtils.blendARGB(surfaceColor, primaryColor, 0.40f)
        return cachedTintedColor
    }

    fun applyFrostedGlassEffect(enabled: Boolean) = applyFrostedGlassProgress(if (enabled) 1f else 0f)

    fun applyFrostedGlassProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0.001f) {
            val isDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val tintedColor = getTintedMonetColor(isDark)
            val maxAlpha = if (isDark) 0x6E else 0x54
            binding.commonScrimView.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tintedColor, (maxAlpha * p).toInt()))
            binding.commonScrimView.alpha = 1f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val density = activity.resources.displayMetrics.density
                val currentBlur = (12f * density).coerceIn(12f, 32f) * p
                if (currentBlur > 1f) {
                    if (kotlin.math.abs(currentBlur - lastAppliedBlur) >= 0.5f || p >= 0.99f) {
                        lastAppliedBlur = currentBlur
                        binding.contentRoot.setRenderEffect(
                            android.graphics.RenderEffect.createBlurEffect(currentBlur, currentBlur, android.graphics.Shader.TileMode.CLAMP)
                        )
                    }
                } else {
                    lastAppliedBlur = 0f
                    binding.contentRoot.setRenderEffect(null)
                }
            }
        } else {
            binding.commonScrimView.alpha = 0f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                lastAppliedBlur = 0f
                binding.contentRoot.setRenderEffect(null)
            }
        }
    }

    private fun resolveMonetThemeColors(isDark: Boolean): Pair<Int, Int> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val cs = if (isDark) androidx.compose.material3.dynamicDarkColorScheme(activity) else androidx.compose.material3.dynamicLightColorScheme(activity)
            cs.surfaceContainer.toArgb() to cs.primary.toArgb()
        } else {
            val cs = if (isDark) com.kododake.aabrowser.ui.compose.theme.DarkPurpleColorScheme else com.kododake.aabrowser.ui.compose.theme.LightPurpleColorScheme
            cs.surfaceContainer.toArgb() to cs.primary.toArgb()
        }
    }

    fun hideMenuOverlay() {
        menuHelper.hideMenu()
    }

    fun onMenuDismissFinished() {
        if (!menuHelper.stateHolder.isMenuVisible) {
            binding.menuComposeView.visibility = View.GONE
            if (hasActiveSubScreen()) return

            binding.menuOverlay.visibility = View.GONE
            applyFrostedGlassEffect(false)
            binding.versionComposeView.visibility = View.GONE
            binding.qrCodeComposeView.visibility = View.GONE
            binding.settingsComposeView.visibility = View.GONE

            callbacks.showMenuButtonTemporarily()
        }
    }

    fun showMenuButtonTemporarily() = callbacks.showMenuButtonTemporarily()

    fun openUriExternally(uri: Uri) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.onFailure { Toast.makeText(activity, R.string.error_open_external, Toast.LENGTH_SHORT).show() }
    }

    fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): Uri? {
        val currentPage = sourceWebView.url ?: return null
        if (!currentPage.startsWith("file:///android_asset/error.html")) return null
        val candidate = rawUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        return if (scheme == "http" || scheme == "https") parsed else null
    }
}
