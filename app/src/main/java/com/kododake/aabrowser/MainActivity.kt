package com.kododake.aabrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.kododake.aabrowser.analytics.UmamiTracker
import com.google.android.material.color.DynamicColors
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.configureWebView
import com.kododake.aabrowser.web.releaseCompletely
import com.kododake.aabrowser.web.updateDesktopMode
import com.kododake.aabrowser.web.updateUserAgentProfile
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import android.widget.RadioGroup
import com.kododake.aabrowser.settings.SettingsViews

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val isDebugBuild: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideMenuFab = Runnable {
        if (::binding.isInitialized) binding.menuFab.hide()
    }
    private val showMenuFabRunnable = Runnable {
        if (!::binding.isInitialized) return@Runnable
        if (isInFullscreen() || binding.menuOverlay.isVisible) return@Runnable
        binding.menuFab.show()
        handler.postDelayed(autoHideMenuFab, MENU_BUTTON_AUTO_HIDE_DELAY_MS)
    }
    private var webView: android.webkit.WebView? = null
    private var currentUrl: String = BrowserPreferences.defaultUrl()
    private var currentUserAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_CHROME
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isShowingCleartextDialog: Boolean = false
    private var latestReleaseUrl: String = "https://github.com/kododake/AABrowser/releases"
    private val umamiTracker: UmamiTracker by lazy { UmamiTracker(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        umamiTracker.trackEvent("app_open")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val disp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                this.display
            } else {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            }
            val best = disp?.supportedModes?.maxWithOrNull(compareBy({ it.refreshRate }, { it.physicalWidth.toLong() * it.physicalHeight }))
            best?.let { mode ->
                val attrs = window.attributes
                attrs.preferredDisplayModeId = mode.modeId
                window.attributes = attrs
            }
        }

        setupUi()
        setupBackPressHandling()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractBrowsableUrl(intent)?.let { loadUrlFromIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        refreshBookmarks()
        syncUserAgentProfile()
    }

    override fun onPause() {
        exitFullscreen()
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoHideMenuFab)
        handler.removeCallbacks(showMenuFabRunnable)
        exitFullscreen()
        binding.webView.releaseCompletely()
        webView = null
        super.onDestroy()
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun setupUi() {
        val intentUrl = extractBrowsableUrl(intent)
        val initialUrl = intentUrl ?: BrowserPreferences.resolveInitialUrl(this)
        currentUrl = initialUrl
        val desktopMode = BrowserPreferences.shouldUseDesktopMode(this)
        currentUserAgentProfile = BrowserPreferences.getUserAgentProfile(this)

        binding.menuFab.hide()

        val callbacks = BrowserCallbacks(
            onUrlChange = { url ->
                runOnUiThread {
                    currentUrl = url
                    if (binding.addressEdit.text?.toString() != url) {
                        binding.addressEdit.setText(url)
                        binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
                    }
                    BrowserPreferences.persistUrl(this, url)
                    updateNavigationButtons()
                    updateConnectionSecurityIcon(url)
                }
            },
            onTitleChange = { title ->
                runOnUiThread { binding.pageTitle.text = title.orEmpty() }
            },
            onProgressChange = { progress ->
                runOnUiThread { updateProgress(progress) }
            },
            onShowDownloadPrompt = { uri ->
                runOnUiThread { openUriExternally(uri) }
            },
            onCleartextNavigationRequested = { uri, allowOnce, allowhostPermanently, cancel ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        cancel()
                        return@runOnUiThread
                    }
                    if (isShowingCleartextDialog) return@runOnUiThread
                    isShowingCleartextDialog = true
                    val host = uri.host ?: uri.toString()
                    val view = layoutInflater.inflate(R.layout.dialog_cleartext_confirmation, null)
                    val titleView = view.findViewById<android.widget.TextView>(R.id.cleartext_title)
                    val messageView = view.findViewById<android.widget.TextView>(R.id.cleartext_message)
                    titleView.text = "Insecure connection"
                    messageView.text = "You are about to open an HTTP (insecure) site: $host. This may expose data to network attackers. What would you like to do?"

                    val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        this,
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                    ).setView(view).create()

                    dialog.setOnDismissListener { isShowingCleartextDialog = false }

                    view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_dialog).setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        cancel()
                    }
                    view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_once).setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        allowOnce()
                    }
                    view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_host).setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        allowhostPermanently()
                    }
                    try {
                        dialog.show()
                        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
                        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                    } catch (e: Exception) {
                        isShowingCleartextDialog = false
                        cancel()
                    }
                }
            },
            onError = { _, description ->
                runOnUiThread {
                    if (isDebugBuild) {
                        val message = description ?: getString(R.string.error_generic_message)
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEnterFullscreen = { view, callback ->
                runOnUiThread { enterFullscreen(view, callback) }
            },
            onExitFullscreen = {
                runOnUiThread { exitFullscreen(true) }
            }
        )

        webView = binding.webView
        webView?.let { view ->
            configureWebView(view, callbacks, desktopMode, currentUserAgentProfile)
            view.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun openExternal(url: String) {
                    if (url.isNullOrBlank()) return
                    runOnUiThread { runCatching { openUriExternally(Uri.parse(url)) } }
                }
            }, "Android")
            view.setOnTouchListener { _, _ ->
                showMenuButtonTemporarily()
                false
            }
            view.loadUrl(initialUrl)
        }

        updateConnectionSecurityIcon(initialUrl)

        if (intentUrl != null) {
            BrowserPreferences.persistUrl(this, initialUrl)
        }

        binding.desktopSwitch.isChecked = desktopMode
        binding.desktopSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setDesktopMode(this, isChecked)
            webView?.updateDesktopMode(isChecked, currentUserAgentProfile)
        }

        binding.addressEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToAddress()
                true
            } else {
                false
            }
        }

        binding.addressEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrEmpty()
                if (hasText && binding.buttonClearAddress.visibility != View.VISIBLE) {
                    binding.buttonClearAddress.visibility = View.VISIBLE
                    binding.buttonClearAddress.alpha = 0f
                    binding.buttonClearAddress.animate().alpha(1f).setDuration(150).start()
                } else if (!hasText && binding.buttonClearAddress.visibility == View.VISIBLE) {
                    binding.buttonClearAddress.animate().alpha(0f).setDuration(100).withEndAction {
                        binding.buttonClearAddress.visibility = View.GONE
                    }.start()
                }
            }
        })

        binding.buttonClearAddress.setOnClickListener {
            binding.addressEdit.setText("")
            binding.addressEdit.requestFocus()
            showKeyboard(binding.addressEdit)
        }

        binding.buttonGo.setOnClickListener { navigateToAddress() }

        binding.buttonReload.setOnClickListener {
            webView?.reload()
            hideMenuOverlay()
        }

        binding.buttonBack.setOnClickListener {
            webView?.let { if (it.canGoBack()) it.goBack() }
            updateNavigationButtons()
        }

        binding.buttonForward.setOnClickListener {
            webView?.let { if (it.canGoForward()) it.goForward() }
            updateNavigationButtons()
        }

        val tonalIconColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val tonalColorStateList = android.content.res.ColorStateList.valueOf(tonalIconColor)
        val primaryColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val primaryColorStateList = android.content.res.ColorStateList.valueOf(primaryColor)

        val navButtons = listOf(
            binding.buttonBack, binding.buttonReload, binding.buttonForward,
            binding.buttonBookmarks, binding.buttonSettings, binding.buttonExternalGithub
        )

        navButtons.forEach { btn ->
            btn.isEnabled = true
            btn.isClickable = true
            btn.iconTint = tonalColorStateList
        }

        binding.buttonExternal.setOnClickListener { showQrCodeView() }
        binding.buttonExternalGithub.iconTint = primaryColorStateList
        binding.buttonExternalGithub.setOnClickListener { openUriExternally(Uri.parse(GITHUB_REPO_URL)) }
        binding.buttonBookmarks.setOnClickListener { showBookmarkManager() }
        binding.buttonBookmarkManagerBack.setOnClickListener { hideBookmarkManager() }
        binding.buttonBookmarkAdd.setOnClickListener { addBookmarkForCurrentPage() }
        binding.buttonQrCodeBack.setOnClickListener { hideQrCodeView() }
        binding.buttonQrCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", currentUrl))
            Toast.makeText(this, "Copied URL", Toast.LENGTH_SHORT).show()
        }
        binding.buttonQrExternalBrowser.setOnClickListener {
            openUriExternally(Uri.parse(currentUrl))
            hideMenuOverlay()
        }
        binding.buttonCheckLatest.setOnClickListener { showCheckLatestView() }
        binding.buttonCheckLatestBack.setOnClickListener { hideCheckLatestView() }
        binding.checkLatestOpenReleaseButton.setOnClickListener {
            openUriExternally(Uri.parse(latestReleaseUrl))
            hideMenuOverlay()
        }
        binding.buttonSettings.setOnClickListener { showSettingsView() }

        binding.menuFab.setOnClickListener { showMenuOverlay() }
        binding.buttonClose.setOnClickListener { hideMenuOverlay() }
        binding.menuOverlayScrim.setOnClickListener { hideMenuOverlay() }

        setupManualDragLogic()

        updateNavigationButtons()
        showMenuButtonTemporarily()
        refreshBookmarks()

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.menuVersion.text = getString(R.string.installed_version_label, "v${pInfo.versionName}")
        } catch (_: Exception) {}
    }

    private fun setupManualDragLogic() {
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

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                isInFullscreen() -> exitFullscreen()
                binding.checkLatestViewRoot.isVisible -> hideCheckLatestView()
                binding.qrCodeViewRoot.isVisible -> hideQrCodeView()
                binding.bookmarkManagerRoot.isVisible -> hideBookmarkManager()
                binding.settingsViewRoot.isVisible -> hideSettingsView()
                binding.menuOverlay.isVisible -> hideMenuOverlay()
                webView?.canGoBack() == true -> webView?.goBack()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            updateNavigationButtons()
        }
    }

    private fun syncUserAgentProfile() {
        val latestProfile = BrowserPreferences.getUserAgentProfile(this)
        if (latestProfile == currentUserAgentProfile) return
        currentUserAgentProfile = latestProfile
        webView?.updateUserAgentProfile(latestProfile, BrowserPreferences.shouldUseDesktopMode(this))
    }

    private fun navigateToAddress() {
        val raw = binding.addressEdit.text?.toString().orEmpty()
        val navigable = BrowserPreferences.formatNavigableUrl(raw)
        currentUrl = navigable
        BrowserPreferences.persistUrl(this, navigable)
        webView?.loadUrl(navigable)
        hideMenuOverlay()
    }

    private fun loadUrlFromIntent(rawUrl: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(rawUrl.trim())
        if (navigable.isEmpty()) return
        currentUrl = navigable
        BrowserPreferences.persistUrl(this, navigable)
        binding.addressEdit.setText(navigable)
        webView?.loadUrl(navigable)
        hideMenuOverlay()
    }

    private fun updateNavigationButtons() {
        binding.buttonBack.isEnabled = webView?.canGoBack() == true
        binding.buttonForward.isEnabled = webView?.canGoForward() == true
    }

    private fun updateConnectionSecurityIcon(url: String?) {
        val isSecure = try { url?.lowercase()?.startsWith("https://") == true } catch (_: Exception) { false }
        binding.addressSecureIcon.visibility = if (isSecure) View.VISIBLE else View.GONE
        binding.addressInsecureIcon.visibility = if (isSecure) View.GONE else View.VISIBLE
    }

    private fun updateProgress(progress: Int) {
        binding.progressIndicator.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
        if (progress in 1..99) binding.progressIndicator.setProgressCompat(progress, true)
    }

    private fun showMenuOverlay() {
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
        }
        handler.removeCallbacks(showMenuFabRunnable)
        binding.menuFab.hide()
        refreshBookmarks()
    }

    private fun hideMenuOverlay() {
        hideKeyboard(binding.addressEdit)
        binding.menuCard.animate()
            .translationY(binding.menuCard.height.toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.menuOverlay.visibility = View.GONE
                hideBookmarkManager()
                hideCheckLatestView()
                hideQrCodeView()
                hideSettingsView()
                showMenuButtonTemporarily()
            }
            .start()
        binding.menuOverlayScrim.animate().alpha(0f).setDuration(200).start()
    }

    private fun showMenuButtonTemporarily() {
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        if (isInFullscreen() || binding.menuOverlay.isVisible) return
        handler.postDelayed(showMenuFabRunnable, MENU_BUTTON_SHOW_DELAY_MS)
    }

    private fun openUriExternally(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(this, R.string.error_open_external, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.post { imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun extractBrowsableUrl(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return if (data.scheme?.lowercase() in listOf("http", "https")) data.toString() else null
    }

    private fun isInFullscreen(): Boolean = customView != null

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback
        if (binding.menuOverlay.isVisible) hideMenuOverlay()
        binding.menuFab.hide()
        binding.webView.visibility = View.INVISIBLE
        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            bringToFront()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.fullscreenContainer).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitFullscreen(fromWebChrome: Boolean = false) {
        if (customView == null) return
        binding.fullscreenContainer.apply { removeAllViews(); visibility = View.GONE }
        binding.webView.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        if (!fromWebChrome) callback?.onCustomViewHidden()
        showMenuButtonTemporarily()
    }

    private fun addBookmarkForCurrentPage() {
        val url = currentUrl.trim()
        if (url.isEmpty()) return
        if (BrowserPreferences.addBookmark(this, url)) {
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
        } else {
            Toast.makeText(this, R.string.bookmark_exists, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeBookmark(url: String) {
        if (BrowserPreferences.removeBookmark(this, url)) {
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
        }
    }

    private fun refreshBookmarks() {
        val container = binding.bookmarkManagerList
        val density = resources.displayMetrics.density
        container.removeAllViews()
        val bookmarks = BrowserPreferences.getBookmarks(this)
        if (bookmarks.isEmpty()) {
            container.addView(MaterialTextView(this).apply {
                text = getString(R.string.menu_bookmark_empty)
                setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
                gravity = android.view.Gravity.CENTER
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            return
        }
        bookmarks.forEach { bookmark ->
            val itemCard = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 12 * density
                setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainer))
                strokeWidth = (1 * density).toInt()
                strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
                setOnClickListener { loadUrlFromIntent(bookmark) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
            }
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (12 * density).toInt() }
            }
            textContainer.addView(MaterialTextView(this).apply {
                text = try { java.net.URI(bookmark).host ?: bookmark } catch (_: Exception) { bookmark }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            })
            textContainer.addView(MaterialTextView(this).apply {
                text = bookmark
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                alpha = 0.7f
            })
            val delBtn = MaterialButton(ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_IconButton_Filled_Tonal)).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                setIconResource(android.R.drawable.ic_menu_delete)
                setIconTint(ColorStateList.valueOf(Color.WHITE))
                iconPadding = 0
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                setBackgroundColor(resolveThemeColor(android.R.attr.colorError))
                setOnClickListener { removeBookmark(bookmark) }
            }
            row.addView(textContainer)
            row.addView(delBtn)
            itemCard.addView(row)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, (8 * density).toInt(), 0, 0)
            container.addView(itemCard, params)
        }
    }

    private fun showBookmarkManager() {
        binding.menuScroll.visibility = View.GONE
        binding.bookmarkManagerRoot.visibility = View.VISIBLE
        refreshBookmarks()
    }

    private fun hideBookmarkManager() {
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun showQrCodeView() {
        val url = currentUrl.trim()
        if (url.isEmpty()) return
        binding.menuScroll.visibility = View.GONE
        binding.qrCodeViewRoot.visibility = View.VISIBLE
        generateQrCode(url)?.let {
            binding.qrCodeImage.setImageBitmap(it)
            binding.qrCodeUrl.text = url
        }
    }

    private fun hideQrCodeView() {
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun showSettingsView() {
        binding.menuScroll.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.VISIBLE
        ensureSettingsContentPopulated()
    }

    private fun ensureSettingsContentPopulated() {
        if (binding.settingsContentContainer.childCount > 0) return
        try {
            val contentView = SettingsViews.createSettingsContent(this, false) {
                hideSettingsView()
            }
            binding.settingsContentContainer.addView(contentView)
        } catch (_: Exception) {}
    }

    private fun hideSettingsView() {
        binding.settingsViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun showCheckLatestView() {
        binding.menuScroll.visibility = View.GONE
        binding.checkLatestViewRoot.visibility = View.VISIBLE
        binding.checkLatestProgressIndicator.visibility = View.VISIBLE
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.checkLatestInstalledVersion.text = getString(R.string.installed_version_label, "v${pInfo.versionName}")
        } catch (_: Exception) {
            binding.checkLatestInstalledVersion.text = "Installed: Unknown"
        }
        Thread {
            try {
                val conn = java.net.URL("https://api.github.com/repos/kododake/AABrowser/releases/latest").openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (conn.responseCode == 200) {
                    val json = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    latestReleaseUrl = json.getString("html_url")
                    val tag = json.getString("tag_name")
                    runOnUiThread {
                        binding.checkLatestProgressIndicator.visibility = View.GONE
                        binding.checkLatestLatestVersion.text = getString(R.string.latest_version_label, tag)
                    }
                }
            } catch (_: Exception) { runOnUiThread { binding.checkLatestProgressIndicator.visibility = View.GONE } }
        }.start()
    }

    private fun hideCheckLatestView() {
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            for (x in 0 until 512) for (y in 0 until 512) bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            bitmap
        } catch (_: Exception) { null }
    }

    companion object {
        private const val MENU_BUTTON_AUTO_HIDE_DELAY_MS = 3000L
        private const val MENU_BUTTON_SHOW_DELAY_MS = 500L
        private const val GITHUB_REPO_URL = "https://github.com/kododake/AABrowser"
    }
}