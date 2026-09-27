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

package com.kododake.aabrowser.ui.controllers

import android.webkit.WebView
import com.kododake.aabrowser.databinding.ActivityMainBinding

/**
 * Controller to update back/forward/reload/external/desktop buttons state.
 */
object NavigationButtonUpdater {

    fun update(
        binding: ActivityMainBinding,
        isShowingStartPage: Boolean,
        currentUrl: String,
        webView: WebView?,
        menuHelper: MenuSetupHelper? = null
    ) {
        val notStart = !isShowingStartPage
        val canNavigate = notStart && currentUrl.isNotBlank()
        val canGoBack = notStart && webView?.canGoBack() == true
        val canGoForward = notStart && webView?.canGoForward() == true
        val canReload = canNavigate
        val canQrCode = canNavigate

        val isDesktop = com.kododake.aabrowser.data.BrowserPreferences.shouldUseDesktopMode(binding.root.context)
        menuHelper?.updateNavigation(canGoBack, canGoForward, canReload, isDesktop, canQrCode)
    }
}
