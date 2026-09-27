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

package com.kododake.aabrowser.web

import android.net.Uri
import android.webkit.WebView
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences

object CleartextNavigationHandler {
    fun handleCleartextIfNeeded(
        view: WebView,
        uri: Uri?,
        callbacks: BrowserCallbacks,
        onPageStart: Boolean = false
    ): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http") return false

        val allowedOnce = view.getTag(R.id.webview_allow_once_uri_tag) as? String
        if (allowedOnce == uri.toString()) {
            view.setTag(R.id.webview_allow_once_uri_tag, null)
            return false
        }

        val host = uri.host?.lowercase()
        if (BrowserPreferences.isHostAllowedCleartext(view.context, host)) {
            return false
        }
        if (onPageStart) view.stopLoading()
        val allowOnce = {
            view.setTag(R.id.webview_allow_once_uri_tag, uri.toString())
            view.post { view.loadUrlWhenShieldsReady(uri.toString()) }
            Unit
        }
        val allowHost = {
            view.context?.let { ctx ->
                val hostToStore = uri.host?.lowercase()
                if (hostToStore != null) BrowserPreferences.addAllowedCleartextHost(ctx, hostToStore)
            }
            view.setTag(R.id.webview_allow_once_uri_tag, uri.toString())
            view.post { view.loadUrlWhenShieldsReady(uri.toString()) }
            Unit
        }
        val cancel = {
            if (onPageStart) view.stopLoading()
            Unit
        }
        callbacks.onCleartextNavigationRequested(uri, allowOnce, allowHost, cancel)
        return true
    }
}
