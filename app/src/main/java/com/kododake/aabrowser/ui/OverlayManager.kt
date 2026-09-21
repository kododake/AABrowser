package com.kododake.aabrowser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.settings.SettingsCallbacks
import com.kododake.aabrowser.settings.SettingsViews
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager
import com.kododake.aabrowser.web.updatePageDarkening
import com.kododake.aabrowser.web.updateShieldsEnabled

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
    }

    fun showQrCodeView(url: String) {
        if (url.isBlank()) {
            return
        }
        
        hideAllOverlays()
        binding.qrCodeViewRoot.visibility = View.VISIBLE
        binding.qrCodeImage.setImageBitmap(null)
        binding.qrCodeUrl.text = url
        
        activity.lifecycleScope.launch {
            val bitmap = QRUtils.generateQrCodeAsync(url)
            if (bitmap != null) {
                binding.qrCodeImage.setImageBitmap(bitmap)
            }
        }
    }

    fun hideQrCodeView() {
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    fun showSettingsView() {
        hideAllOverlays()
        binding.settingsViewRoot.visibility = View.VISIBLE
        ensureSettingsContentPopulated()
    }

    fun hideSettingsView() {
        binding.settingsViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    fun showCheckLatestView() {
        hideAllOverlays()
        binding.checkLatestViewRoot.visibility = View.VISIBLE
        binding.checkLatestProgressIndicator.visibility = View.VISIBLE
        
        binding.checkLatestLatestVersion.text = activity.getString(R.string.menu_checking_latest)
        binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary))

        val packageName = activity.packageName
        binding.checkLatestInstalledVersion.text = activity.getString(
            R.string.installed_version_label, 
            "v${com.kododake.aabrowser.BuildConfig.VERSION_NAME}"
        )
        
        fetchLatestVersion()
    }

    fun hideCheckLatestView() {
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun hideAllOverlays() {
        val views = listOf(
            binding.menuScroll,
            binding.bookmarkManagerRoot,
            binding.tabManagerRoot,
            binding.checkLatestViewRoot,
            binding.settingsViewRoot,
            binding.qrCodeViewRoot
        )
        views.forEach { 
            it.visibility = View.GONE 
        }
    }

    private fun ensureSettingsContentPopulated() {
        if (binding.settingsContentContainer.childCount > 0) {
            return
        }
        
        try {
            val settingsCallbacks = SettingsCallbacks(
                onClose = { 
                    hideSettingsView() 
                },
                onThemeChanged = { 
                    callbacks.onRecreateRequested() 
                },
                onPageDarkeningChanged = {
                    val enabled = BrowserPreferences.isBetaForceDarkPagesEnabled(activity)
                    tabManager.browserTabs.forEach { tab ->
                        tab.webView.updatePageDarkening(enabled)
                    }
                },
                onScaleChanged = { 
                    callbacks.onRecreateRequested() 
                },
                onHomePageChanged = { 
                    callbacks.onHomePageChanged() 
                },
                onInAppControlsChanged = {
                    uiManager.applyPersistentAddressBarPreference()
                    uiManager.applyQuickActionButtonPreferences()
                },
                onPickStartPageBackground = {
                    callbacks.onPickBackgroundRequested()
                },
                onClearStartPageBackground = { 
                    startPageManager.clearStartPageBackground() 
                },
                onSponsorsVisibilityChanged = {
                    startPageManager.refreshStartPage()
                },
                onShieldsChanged = {
                    val enabled = BrowserPreferences.isShieldsEnabled(activity)
                    tabManager.browserTabs.forEach { tab ->
                        tab.webView.updateShieldsEnabled(enabled)
                    }
                }
            )
            
            val contentView = SettingsViews.createSettingsContent(activity, false, settingsCallbacks)
            binding.settingsContentContainer.addView(contentView)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    private fun fetchLatestVersion() {
        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/kododake/AABrowser/releases/latest")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                    val json = org.json.JSONObject(response)
                    val latestUrl = json.getString("html_url")
                    val tag = json.getString("tag_name")
                    
                    activity.runOnUiThread {
                        binding.checkLatestProgressIndicator.visibility = View.GONE
                        val currentVer = com.kododake.aabrowser.BuildConfig.VERSION_NAME.trim().removePrefix("v")
                        val latestVer = tag.trim().removePrefix("v")
                        
                        if (currentVer.equals(latestVer, ignoreCase = true)) {
                            binding.checkLatestLatestVersion.text = activity.getString(R.string.check_latest_up_to_date, latestVer)
                            binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                        } else {
                            binding.checkLatestLatestVersion.text = activity.getString(R.string.check_latest_update_available, tag)
                            binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorError))
                        }
                        callbacks.onVersionInfoReceived(latestUrl, tag)
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { 
                    binding.checkLatestProgressIndicator.visibility = View.GONE 
                }
            }
        }.start()
    }

    private fun getColorFromAttr(attrResId: Int): Int {
        val tv = android.util.TypedValue()
        if (activity.theme.resolveAttribute(attrResId, tv, true)) {
            if (tv.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(activity, tv.resourceId)
            }
            return tv.data
        }
        return android.graphics.Color.TRANSPARENT
    }
}
