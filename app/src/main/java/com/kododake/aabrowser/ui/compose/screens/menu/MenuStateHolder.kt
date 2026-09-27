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

package com.kododake.aabrowser.ui.compose.screens.menu

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable UI state holder for the Compose browser menu sheet.
 */
class MenuStateHolder {
    var url by mutableStateOf("")
    var pageTitle by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var canReload by mutableStateOf(false)
    var canQrCode by mutableStateOf(false)
    var isDesktopMode by mutableStateOf(false)
    var isFullscreenMode by mutableStateOf(false)
    /** Global Shields switch from Settings; the per-site card is disabled while this is off. */
    var isShieldsEnabled by mutableStateOf(true)
    /** Host of the active tab's page, or null when no web page is open. */
    var shieldsSiteHost by mutableStateOf<String?>(null)
    /** Whether Shields is on for [shieldsSiteHost]. */
    var isShieldsOnForSite by mutableStateOf(true)
    var versionName by mutableStateOf("unknown")
    var isMenuVisible by mutableStateOf(false)
    var isReturningFromSubscreen by mutableStateOf(false)
    var isFabVisible by mutableStateOf(false)
    var fabIsAddressBarMode by mutableStateOf(false)

    fun updateNavigationState(
        canBack: Boolean,
        canForward: Boolean,
        canRel: Boolean,
        desktop: Boolean,
        canQr: Boolean = false
    ) {
        canGoBack = canBack
        canGoForward = canForward
        canReload = canRel
        isDesktopMode = desktop
        canQrCode = canQr
    }

    fun updateShieldsState(globallyEnabled: Boolean, host: String?, onForSite: Boolean) {
        isShieldsEnabled = globallyEnabled
        shieldsSiteHost = host
        isShieldsOnForSite = onForSite
    }

    fun updatePageInfo(newUrl: String, title: String? = null) {
        val decoded = Uri.decode(newUrl)
        if (url != decoded) {
            url = decoded
        }
        if (title != null && pageTitle != title) {
            pageTitle = title
        }
    }
}
