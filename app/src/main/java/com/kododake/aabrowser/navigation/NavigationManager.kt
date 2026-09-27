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

package com.kododake.aabrowser.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.permissions.PermissionManager
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.BrowserUIManager
import com.kododake.aabrowser.ui.controllers.ShieldsLoadingIndicator
import com.kododake.aabrowser.web.loadUrlWhenShieldsReady

class NavigationManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val permissionManager: PermissionManager,
    private val startPageManager: StartPageManager,
    private val uiManager: BrowserUIManager,
    private val callbacks: NavigationCallbacks
) {

    interface NavigationCallbacks {
        fun onNavigationStarted(url: String)
        fun onNavigationFinished(url: String)
        fun onHideStartPage()
        fun getCurrentUrl(): String
        fun setCurrentUrl(url: String)
        fun setCurrentPageTitle(title: String)
    }

    fun extractBrowsableUrl(intent: Intent?): String? {
        val data = intent?.data
        if (data == null) {
            return null
        }
        val scheme = data.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return data.toString()
        }
        return null
    }

    fun loadUrlFromIntent(rawUrl: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(rawUrl.trim())
        if (navigable.isNotEmpty()) {
            navigateActiveTabTo(navigable, closeMenuAfterNavigate = true)
        }
    }

    fun navigateFromBookmark(rawUrl: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(rawUrl.trim())
        if (navigable.isNotEmpty()) {
            navigateActiveTabTo(navigable, closeMenuAfterNavigate = true, resetSessionIfPageOpen = true)
        }
    }

    fun navigateToAddress(raw: String, closeMenuAfterNavigate: Boolean) {
        val navigable = BrowserPreferences.formatNavigableUrl(raw)
        if (navigable.isNotEmpty()) {
            navigateActiveTabTo(navigable, closeMenuAfterNavigate)
        }
    }

    private fun navigateActiveTabTo(
        navigable: String,
        closeMenuAfterNavigate: Boolean,
        resetSessionIfPageOpen: Boolean = false
    ) {
        var targetTab = tabManager.activeTab
        if (targetTab == null) {
            targetTab = tabManager.createNewTab(activate = true)
        }
        if (targetTab == null) {
            return
        }

        if (resetSessionIfPageOpen && !startPageManager.isShowingStartPage) {
            val freshTab = tabManager.resetActiveTabSession(navigable)
            if (freshTab != null) {
                targetTab = freshTab
            }
        }
        
        val targetWebView = targetTab.webView
        val uri = runCatching { Uri.parse(navigable) }.getOrNull()
        if (uri == null) {
            return
        }

        val finishNavigation: (() -> Unit) -> Unit = { loadAction ->
            tabManager.updateTabUrlAndTitle(targetTab.id, navigable, "")
            
            if (targetTab.id == tabManager.activeTabId) {
                callbacks.setCurrentUrl(navigable)
                callbacks.setCurrentPageTitle("")
            }
            
            BrowserPreferences.persistUrl(activity, navigable)
            val isFromStartPage = startPageManager.isShowingStartPage
            if (isFromStartPage) {
                targetWebView.visibility = View.VISIBLE
                callbacks.onHideStartPage()
            }
            startPageManager.beginNavigationLoading(fromStartPage = isFromStartPage)
            loadAction()
            
            if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                uiManager.hideMenuOverlay()
            }
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        
        if (scheme == "http" && !BrowserPreferences.isHostAllowedCleartext(activity, host)) {
            permissionManager.showCleartextNavigationDialog(
                uri = uri,
                onAllowOnce = {
                    finishNavigation {
                        targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                        targetWebView.post { 
                            targetWebView.loadUrlWhenShieldsReady(navigable, ::showShieldsLoading)
                        }
                    }
                },
                onAllowHost = {
                    if (host != null) {
                        BrowserPreferences.addAllowedCleartextHost(activity, host)
                    }
                    finishNavigation {
                        targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                        targetWebView.post { 
                            targetWebView.loadUrlWhenShieldsReady(navigable, ::showShieldsLoading)
                        }
                    }
                },
                onCancel = {
                    if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                        uiManager.hideMenuOverlay()
                    }
                }
            )
            return
        }

        finishNavigation { 
            targetWebView.loadUrlWhenShieldsReady(navigable, ::showShieldsLoading)
        }
    }

    private fun showShieldsLoading(waiting: Boolean) {
        ShieldsLoadingIndicator.show(binding, waiting)
    }
}
