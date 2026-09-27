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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.kododake.aabrowser.BuildConfig
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.SettingsViews
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.ui.compose.screens.share.QrViews
import com.kododake.aabrowser.ui.compose.screens.version.VersionFetcher
import com.kododake.aabrowser.ui.compose.screens.version.VersionViews
import kotlinx.coroutines.launch

class OverlayManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val bookmarkManager: BookmarkManager,
    private val startPageManager: StartPageManager,
    private val uiManager: BrowserUIManager,
    private val callbacks: OverlayCallbacks
) {

    interface OverlayCallbacks {
        fun onRecreateRequested()
        fun onHomePageChanged()
        fun onPickBackgroundRequested()
        fun onVersionInfoReceived(latestUrl: String, tagName: String)
        fun onReturnToMenuRequested() {}
        fun onDismissOverlaysRequested() {}
        fun onScreenProgress(screen: OverlayNavigationCoordinator.OverlayScreen, progress: Float) {}
    }

    private val qrIsVisibleState = mutableStateOf(false)
    private val qrUrlState = mutableStateOf("")
    private val qrBitmapState = mutableStateOf<Bitmap?>(null)

    private val versionIsVisibleState = mutableStateOf(false)
    private val isCheckingState = mutableStateOf(true)
    private val latestVersionState = mutableStateOf<String?>(null)
    private val releaseUrlState = mutableStateOf<String?>(null)

    private val settingsIsVisibleState = mutableStateOf(false)

    var isQrOpenedFromMenu = false
        private set
    var isVersionOpenedFromMenu = false
        private set
    var isSettingsOpenedFromMenu = false
        private set

    init {
        setupComposeViews()
    }

    private fun setupComposeViews() {
        QrViews.setup(
            composeView = binding.qrCodeComposeView,
            isVisibleState = qrIsVisibleState,
            urlState = qrUrlState,
            qrBitmapState = qrBitmapState,
            animateEnterProvider = { true },
            onCopyUrl = {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("URL", qrUrlState.value))
                Toast.makeText(activity, "URL copied", Toast.LENGTH_SHORT).show()
            },
            onShareExternal = {
                val url = qrUrlState.value
                if (url.isNotBlank()) {
                    uiManager.openUriExternally(Uri.parse(url))
                }
            },
            onClose = {
                if (isQrOpenedFromMenu) returnQrToMenu() else hideQrCodeView()
            },
            onDismiss = { hideQrCodeView() },
            onDismissFinished = { onQrDismissFinished() },
            onProgress = { callbacks.onScreenProgress(OverlayNavigationCoordinator.OverlayScreen.QR_CODE, it) }
        )

        VersionViews.setup(
            composeView = binding.versionComposeView,
            isVisibleState = versionIsVisibleState,
            isCheckingState = isCheckingState,
            latestVersionState = latestVersionState,
            installedVersion = "v${BuildConfig.VERSION_NAME}",
            releaseUrlState = releaseUrlState,
            animateEnterProvider = { true },
            onOpenRelease = { url ->
                uiManager.openUriExternally(Uri.parse(url))
            },
            onClose = {
                if (isVersionOpenedFromMenu) returnVersionToMenu() else hideCheckLatestView()
            },
            onDismiss = { hideCheckLatestView() },
            onDismissFinished = { onVersionDismissFinished() },
            onProgress = { callbacks.onScreenProgress(OverlayNavigationCoordinator.OverlayScreen.VERSION, it) }
        )

        val settingsCallbacks = SettingsCallbacksFactory.create(
            activity = activity,
            tabManager = tabManager,
            startPageManager = startPageManager,
            uiManager = uiManager,
            callbacks = callbacks,
            onClose = {
                if (isSettingsOpenedFromMenu) returnSettingsToMenu() else hideSettingsView()
            },
            onDismiss = { hideSettingsView() }
        )

        SettingsViews.setup(
            composeView = binding.settingsComposeView,
            isVisibleState = settingsIsVisibleState,
            animateEnterProvider = { true },
            callbacks = settingsCallbacks,
            onDismissFinished = { onSettingsDismissFinished() },
            onProgress = { callbacks.onScreenProgress(OverlayNavigationCoordinator.OverlayScreen.SETTINGS, it) }
        )
    }

    fun returnQrToMenu() {
        qrIsVisibleState.value = false
        binding.qrCodeComposeView.visibility = View.GONE
        callbacks.onReturnToMenuRequested()
    }

    fun returnVersionToMenu() {
        versionIsVisibleState.value = false
        binding.versionComposeView.visibility = View.GONE
        callbacks.onReturnToMenuRequested()
    }

    fun returnSettingsToMenu() {
        settingsIsVisibleState.value = false
        binding.settingsComposeView.visibility = View.GONE
        callbacks.onReturnToMenuRequested()
    }

    fun showQrCodeView(url: String, fromMenu: Boolean = false) {
        if (url.isBlank()) return
        isQrOpenedFromMenu = fromMenu
        hideOtherSubScreens()
        binding.menuOverlay.visibility = View.VISIBLE
        binding.qrCodeComposeView.visibility = View.VISIBLE
        qrUrlState.value = url
        qrBitmapState.value = null
        qrIsVisibleState.value = true

        activity.lifecycleScope.launch {
            val bitmap = QRUtils.generateQrCodeAsync(url)
            if (bitmap != null) {
                qrBitmapState.value = bitmap
            }
        }
    }

    fun hideQrCodeView() {
        qrIsVisibleState.value = false
    }

    private fun onQrDismissFinished() {
        if (!qrIsVisibleState.value) {
            binding.qrCodeComposeView.visibility = View.GONE
            checkHideMenuOverlay()
        }
    }

    fun showSettingsView(fromMenu: Boolean = false) {
        isSettingsOpenedFromMenu = fromMenu
        hideOtherSubScreens()
        binding.menuOverlay.visibility = View.VISIBLE
        binding.settingsComposeView.visibility = View.VISIBLE
        settingsIsVisibleState.value = true
    }

    fun hideSettingsView() {
        settingsIsVisibleState.value = false
    }

    private fun onSettingsDismissFinished() {
        if (!settingsIsVisibleState.value) {
            binding.settingsComposeView.visibility = View.GONE
            checkHideMenuOverlay()
        }
    }

    fun showCheckLatestView(fromMenu: Boolean = false) {
        isVersionOpenedFromMenu = fromMenu
        hideOtherSubScreens()
        binding.menuOverlay.visibility = View.VISIBLE
        binding.versionComposeView.visibility = View.VISIBLE
        isCheckingState.value = true
        latestVersionState.value = null
        releaseUrlState.value = null
        versionIsVisibleState.value = true

        fetchLatestVersion()
    }

    fun hideCheckLatestView() {
        versionIsVisibleState.value = false
    }

    private fun onVersionDismissFinished() {
        if (!versionIsVisibleState.value) {
            binding.versionComposeView.visibility = View.GONE
            checkHideMenuOverlay()
        }
    }

    fun hideAllOverlays() {
        qrIsVisibleState.value = false
        versionIsVisibleState.value = false
        settingsIsVisibleState.value = false
        binding.qrCodeComposeView.visibility = View.GONE
        binding.versionComposeView.visibility = View.GONE
        binding.settingsComposeView.visibility = View.GONE
        binding.menuComposeView.visibility = View.GONE
        binding.bookmarkComposeView.visibility = View.GONE
        binding.tabComposeView.visibility = View.GONE
        binding.menuOverlay.visibility = View.GONE
        uiManager.applyFrostedGlassProgress(0f)
        uiManager.showMenuButtonTemporarily()
    }

    private fun hideOtherSubScreens() {
        listOf(binding.menuComposeView, binding.bookmarkComposeView, binding.tabComposeView,
            binding.qrCodeComposeView, binding.versionComposeView, binding.settingsComposeView)
            .forEach { it.visibility = View.GONE }
    }

    private fun checkHideMenuOverlay() {
        val hasActiveSubScreen = binding.qrCodeComposeView.isVisible ||
            binding.versionComposeView.isVisible ||
            binding.settingsComposeView.isVisible ||
            binding.bookmarkComposeView.isVisible ||
            binding.tabComposeView.isVisible ||
            binding.menuComposeView.isVisible
        if (!hasActiveSubScreen) {
            callbacks.onDismissOverlaysRequested()
        }
    }

    private fun fetchLatestVersion() {
        VersionFetcher.fetchLatestVersion(
            activity = activity,
            onSuccess = { latestUrl, tag ->
                isCheckingState.value = false
                latestVersionState.value = tag
                releaseUrlState.value = latestUrl
                callbacks.onVersionInfoReceived(latestUrl, tag)
            },
            onError = {
                isCheckingState.value = false
            }
        )
    }
}
