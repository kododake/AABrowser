package com.kododake.aabrowser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.startpage.StartPageManager
import com.kododake.aabrowser.tabs.TabManager

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

    private var isSyncingAddressFields: Boolean = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    fun configureAddressField(
        editText: com.google.android.material.textfield.TextInputEditText,
        clearButton: MaterialButton,
        goButton: MaterialButton,
        closeMenuAfterNavigate: Boolean
    ) {
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                callbacks.onNavigateToAddress(
                    editText.text?.toString().orEmpty(), 
                    closeMenuAfterNavigate
                )
                true
            } else {
                false
            }
        }

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                syncAddressFieldsFrom(editText)
                updateAddressClearButtons()
            }
        })

        clearButton.setOnClickListener {
            editText.setText("")
            editText.requestFocus()
            showKeyboard(editText)
        }

        goButton.setOnClickListener {
            callbacks.onNavigateToAddress(
                editText.text?.toString().orEmpty(), 
                closeMenuAfterNavigate
            )
        }
    }

    fun syncAddressFieldsFrom(source: com.google.android.material.textfield.TextInputEditText) {
        if (isSyncingAddressFields) {
            return
        }
        
        val text = source.text?.toString().orEmpty()
        isSyncingAddressFields = true
        try {
            val addressEdit = binding.addressEdit
            val persistentAddressEdit = binding.persistentAddressEdit
            
            if (addressEdit !== source && addressEdit.text?.toString() != text) {
                addressEdit.setText(text)
                if (addressEdit.hasFocus()) {
                    addressEdit.setSelection(text.length)
                }
            }
            
            if (persistentAddressEdit !== source && persistentAddressEdit.text?.toString() != text) {
                persistentAddressEdit.setText(text)
                if (persistentAddressEdit.hasFocus()) {
                    persistentAddressEdit.setSelection(text.length)
                }
            }
        } finally {
            isSyncingAddressFields = false
        }
    }

    fun updateAddressClearButtons() {
        val hasAddressText = !binding.addressEdit.text.isNullOrEmpty()
        updateAddressClearButton(binding.buttonClearAddress, hasAddressText)
        
        val hasPersistentAddressText = !binding.persistentAddressEdit.text.isNullOrEmpty()
        updateAddressClearButton(binding.persistentButtonClearAddress, hasPersistentAddressText)
    }

    fun updateConnectionSecurityIcon(url: String?) {
        val icons = listOf(
            binding.addressSecureIcon, 
            binding.addressInsecureIcon, 
            binding.persistentAddressSecureIcon, 
            binding.persistentAddressInsecureIcon
        )
        
        if (url.isNullOrBlank()) {
            icons.forEach { it.visibility = View.GONE }
            return
        }
        
        val isSecure = try { 
            url.lowercase().startsWith("https://") 
        } catch (_: Exception) { 
            false 
        }
        
        binding.addressSecureIcon.isVisible = isSecure
        binding.addressInsecureIcon.isVisible = !isSecure
        binding.persistentAddressSecureIcon.isVisible = isSecure
        binding.persistentAddressInsecureIcon.isVisible = !isSecure
    }

    private fun updateAddressClearButton(button: View, shouldShow: Boolean) {
        if (shouldShow && button.visibility != View.VISIBLE) {
            button.visibility = View.VISIBLE
            button.alpha = 0f
            button.animate().alpha(1f).setDuration(150).start()
        } else if (!shouldShow && button.visibility == View.VISIBLE) {
            button.animate().alpha(0f).setDuration(100).withEndAction {
                button.visibility = View.GONE
            }.start()
        }
    }

    fun applyPersistentAddressBarPreference() {
        val shouldShow = BrowserPreferences.shouldAlwaysShowUrlBar(activity) && !isInFullscreen()
        binding.persistentAddressBarCard.isVisible = shouldShow

        val extraTopPadding = if (shouldShow) {
            persistentAddressBarHeightPx()
        } else {
            0
        }
        
        tabManager.browserTabs.forEach { tab ->
            tab.webView.setPadding(0, extraTopPadding, 0, 0)
        }

        val density = activity.resources.displayMetrics.density
        val startPagePadding = (24 * density).toInt()
        binding.startPageScroll.updatePadding(
            left = startPagePadding,
            top = startPagePadding + extraTopPadding,
            right = startPagePadding,
            bottom = startPagePadding
        )

        updateAddressClearButtons()
        applyQuickActionButtonPreferences()
    }

    private fun persistentAddressBarHeightPx(): Int {
        val density = activity.resources.displayMetrics.density
        return (76 * density).toInt()
    }

    fun applyQuickActionButtonPreferences() {
        val mode = BrowserPreferences.getQuickActionButtonMode(activity)
        val iconRes = if (mode == QuickActionButtonMode.ADDRESS_BAR) {
            R.drawable.search_24px
        } else {
            android.R.drawable.ic_menu_more
        }
        binding.menuFab.setImageResource(iconRes)
        
        val descriptionRes = if (mode == QuickActionButtonMode.ADDRESS_BAR) {
            R.string.menu_open_address_bar
        } else {
            R.string.menu_open_description
        }
        binding.menuFab.contentDescription = activity.getString(descriptionRes)

        val density = activity.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val position = BrowserPreferences.getQuickActionButtonPosition(activity)
        val layoutParams = binding.menuFab.layoutParams as CoordinatorLayout.LayoutParams
        
        layoutParams.gravity = when (position) {
            QuickActionButtonPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            QuickActionButtonPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
            QuickActionButtonPosition.TOP_LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            QuickActionButtonPosition.TOP_RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
        }

        val topOffset = if (position == QuickActionButtonPosition.TOP_LEFT || position == QuickActionButtonPosition.TOP_RIGHT) {
            val barHeight = if (binding.persistentAddressBarCard.isVisible) {
                persistentAddressBarHeightPx()
            } else {
                0
            }
            margin + barHeight
        } else {
            margin
        }
        
        layoutParams.setMargins(margin, topOffset, margin, margin)
        binding.menuFab.layoutParams = layoutParams

        val alwaysVisible = BrowserPreferences.isQuickActionButtonAlwaysVisible(activity)
        if (startPageManager.isShowingStartPage || alwaysVisible) {
            if (!isInFullscreen() && !binding.menuOverlay.isVisible) {
                binding.menuFab.show()
            }
        }
    }

    fun focusMenuAddressBar() {
        binding.addressEdit.requestFocus()
        val length = binding.addressEdit.text?.length ?: 0
        binding.addressEdit.setSelection(length)
        showKeyboard(binding.addressEdit)
    }

    fun showMenuOverlay(focusAddressBar: Boolean = false) {
        binding.menuOverlay.visibility = View.VISIBLE
        binding.menuCard.post {
            binding.menuCard.translationY = binding.menuCard.height.toFloat()
            binding.menuCard.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
                
            binding.menuOverlayScrim.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
                
            if (focusAddressBar) {
                focusMenuAddressBar()
            }
        }
        binding.menuFab.hide()
        bookmarkManager.refreshBookmarks()
        tabManager.refreshTabs()
        startPageManager.refreshStartPage()
        refreshShieldsSiteButton()
    }

    /** Reflects the active tab's per-site Shields state on the menu button. */
    fun refreshShieldsSiteButton() {
        val button = binding.buttonShieldsSite
        val host = shieldsSiteHost()
        when {
            !BrowserPreferences.isShieldsEnabled(activity) -> {
                button.isEnabled = false
                button.text = activity.getString(R.string.menu_shields_site_disabled)
            }
            host == null -> {
                button.isEnabled = false
                button.text = activity.getString(R.string.menu_shields_site_unavailable)
            }
            BrowserPreferences.isShieldsDisabledForHost(activity, host) -> {
                button.isEnabled = true
                button.text = activity.getString(R.string.menu_shields_site_off, host)
            }
            else -> {
                button.isEnabled = true
                button.text = activity.getString(R.string.menu_shields_site_on, host)
            }
        }
    }

    /** Host of the active tab's page when it is a web page, otherwise null. */
    fun shieldsSiteHost(): String? {
        val url = tabManager.activeTab?.webView?.url ?: tabManager.activeTab?.currentUrl
        val uri = url?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        return uri.host?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    fun hideMenuOverlay() {
        hideKeyboard(binding.addressEdit)
        binding.menuCard.animate()
            .translationY(binding.menuCard.height.toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.menuOverlay.visibility = View.GONE
                bookmarkManager.hideBookmarkManager()
                tabManager.hideTabManager()
                
                binding.checkLatestViewRoot.visibility = View.GONE
                binding.qrCodeViewRoot.visibility = View.GONE
                binding.settingsViewRoot.visibility = View.GONE
                
                callbacks.showMenuButtonTemporarily()
            }
            .start()
            
        binding.menuOverlayScrim.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
    }

    fun openUriExternally(uri: Uri) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }.onFailure {
            Toast.makeText(activity, R.string.error_open_external, Toast.LENGTH_SHORT).show()
        }
    }

    fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): Uri? {
        val currentPage = sourceWebView.url
        if (currentPage == null) {
            return null
        }
        if (!currentPage.startsWith("file:///android_asset/error.html")) {
            return null
        }

        val candidate = rawUrl?.trim()
        if (candidate.isNullOrBlank()) {
            return null
        }
        
        val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
        if (parsed == null) {
            return null
        }
        val scheme = parsed.scheme?.lowercase()
        if (scheme == null) {
            return null
        }
        if (scheme != "http" && scheme != "https") {
            return null
        }
        
        return parsed
    }

    fun showKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.post { 
            imm.showSoftInput(view, 0) 
        }
    }

    fun hideKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun isInFullscreen(): Boolean {
        return customView != null
    }

    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        
        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback
        
        if (binding.menuOverlay.isVisible) {
            hideMenuOverlay()
        }
        
        binding.menuFab.hide()
        binding.persistentAddressBarCard.visibility = View.GONE
        tabManager.activeTab?.webView?.visibility = View.INVISIBLE
        
        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            bringToFront()
        }
        
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(activity.window, binding.fullscreenContainer)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun setupManualDragLogic() {
        var startY = 0f
        var initialTranslationY = 0f
        val swipeThreshold = 250f

        binding.dragHandleArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    initialTranslationY = binding.menuCard.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) {
                        binding.menuCard.translationY = initialTranslationY + deltaY
                        val progress = (deltaY / binding.menuCard.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        binding.menuOverlayScrim.alpha = 1f - progress
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val totalDeltaY = event.rawY - startY
                    if (totalDeltaY > swipeThreshold) {
                        hideMenuOverlay()
                    } else {
                        binding.menuCard.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                        binding.menuOverlayScrim.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun exitFullscreen(fromWebChrome: Boolean = false) {
        if (customView == null) {
            return
        }
        
        binding.fullscreenContainer.apply { 
            removeAllViews()
            visibility = View.GONE 
        }
        
        val webViewVisibility = if (startPageManager.isShowingStartPage) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
        tabManager.activeTab?.webView?.visibility = webViewVisibility
        
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(activity.window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())
        
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        
        if (!fromWebChrome) {
            callback?.onCustomViewHidden()
        }
        
        applyPersistentAddressBarPreference()
        callbacks.showMenuButtonTemporarily()
    }
}
