package com.kododake.aabrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.color.DynamicColors
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.configureWebView
import com.kododake.aabrowser.web.releaseCompletely
import com.kododake.aabrowser.web.updateDesktopMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val isDebugBuild: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideMenuFab = Runnable { binding.menuFab.hide() }
    private val showMenuFabRunnable = Runnable {
        if (isInFullscreen() || binding.menuOverlay.isVisible) {
            return@Runnable
        }
        binding.menuFab.show()
        handler.postDelayed(autoHideMenuFab, MENU_BUTTON_AUTO_HIDE_DELAY_MS)
    }
    private var webView: android.webkit.WebView? = null
    private var currentUrl: String = BrowserPreferences.defaultUrl()
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isShowingCleartextDialog: Boolean = false
    private var latestReleaseUrl: String = "https://github.com/kododake/AABrowser/releases"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun setupUi() {
        val intentUrl = extractBrowsableUrl(intent)
        val initialUrl = intentUrl ?: BrowserPreferences.resolveInitialUrl(this)
        currentUrl = initialUrl
        val desktopMode = BrowserPreferences.shouldUseDesktopMode(this)

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
                    val inflater = layoutInflater
                    val view = inflater.inflate(R.layout.dialog_cleartext_confirmation, null)
                    val titleView = view.findViewById<android.widget.TextView>(R.id.cleartext_title)
                    val messageView = view.findViewById<android.widget.TextView>(R.id.cleartext_message)
                    titleView.text = "Insecure connection"
                    messageView.text = "You are about to open an HTTP (insecure) site: $host. This may expose data to network attackers. What would you like to do?"

                    val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        this,
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                    ).setView(view).create()

                    dialog.setOnDismissListener {
                        isShowingCleartextDialog = false
                    }

                    val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_dialog)
                    val btnAllowOnce = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_once)
                    val btnAllowhost = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_host)

                    btnCancel.setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        cancel()
                    }

                    btnAllowOnce.setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        allowOnce()
                    }

                    btnAllowhost.setOnClickListener {
                        try { dialog.dismiss() } catch (_: Exception) {}
                        allowhostPermanently()
                    }

                    try {
                        dialog.show()
                        val w = dialog.window
                        val metrics = resources.displayMetrics
                        val width = (metrics.widthPixels * 0.9).toInt()
                        w?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
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
            configureWebView(view, callbacks, desktopMode)
            view.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun openExternal(url: String) {
                    if (url.isNullOrBlank()) return
                    runOnUiThread {
                        runCatching {
                            openUriExternally(Uri.parse(url))
                        }
                    }
                }
            }, "Android")
            view.setOnTouchListener { _, _ ->
                showMenuButtonTemporarily()
                false
            }
            view.loadUrl(initialUrl)
        }

        if (intentUrl != null) {
            BrowserPreferences.persistUrl(this, initialUrl)
        }

        binding.desktopSwitch.isChecked = desktopMode
        binding.desktopSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setDesktopMode(this, isChecked)
            webView?.updateDesktopMode(isChecked)
        }

        binding.menuFab.setOnClickListener { showMenuOverlay() }
        binding.menuOverlayScrim.setOnClickListener { hideMenuOverlay() }
        binding.menuCard.setOnClickListener { /* consume click */ }
        setupMenuSwipeToClose()

        binding.addressEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToAddress()
                true
            } else {
                false
            }
        }

        // Show/hide clear button based on text content
        binding.addressEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrEmpty()
                if (hasText && binding.buttonClearAddress.visibility != View.VISIBLE) {
                    binding.buttonClearAddress.visibility = View.VISIBLE
                    binding.buttonClearAddress.alpha = 0f
                    binding.buttonClearAddress.scaleX = 0.8f
                    binding.buttonClearAddress.scaleY = 0.8f
                    binding.buttonClearAddress.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                } else if (!hasText && binding.buttonClearAddress.visibility == View.VISIBLE) {
                    binding.buttonClearAddress.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(100)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction {
                            binding.buttonClearAddress.visibility = View.GONE
                        }
                        .start()
                }
            }
        })

        binding.buttonClearAddress.setOnClickListener {
            binding.addressEdit.setText("")
            binding.addressEdit.requestFocus()
            showKeyboard(binding.addressEdit)
        }


        binding.buttonClose.setOnClickListener { hideMenuOverlay() }
        binding.buttonGo.setOnClickListener { navigateToAddress() }
        binding.buttonReload.setOnClickListener { webView?.reload() }
        binding.buttonBack.setOnClickListener {
            webView?.let {
                if (it.canGoBack()) {
                    it.goBack()
                }
            }
            updateNavigationButtons()
        }
        binding.buttonForward.setOnClickListener {
            webView?.let {
                if (it.canGoForward()) {
                    it.goForward()
                }
            }
            updateNavigationButtons()
        }
        binding.buttonExternal.setOnClickListener {
            showQrCodeView()
        }

        binding.buttonExternalGithub.setOnClickListener {
            openUriExternally(Uri.parse(GITHUB_REPO_URL))
        }


        binding.buttonBookmarks.setOnClickListener { showBookmarkManager() }
        binding.buttonBookmarkManagerBack.setOnClickListener { hideBookmarkManager() }
        binding.buttonBookmarkAdd.setOnClickListener { addBookmarkForCurrentPage() }

        binding.buttonQrCodeBack.setOnClickListener { hideQrCodeView() }

        binding.buttonCheckLatest.setOnClickListener {
            showCheckLatestView()
        }
        binding.buttonCheckLatestBack.setOnClickListener { hideCheckLatestView() }
        binding.checkLatestOpenReleaseButton.setOnClickListener {
            openUriExternally(Uri.parse(latestReleaseUrl))
        }

        updateNavigationButtons()
        showMenuButtonTemporarily()
        refreshBookmarks()

        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = pInfo.versionName ?: ""
            binding.menuVersion.text = getString(R.string.installed_version_label, "v$versionName")
        } catch (_: Exception) {
            
        }
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                isInFullscreen() -> exitFullscreen()
                binding.checkLatestViewRoot.isVisible -> hideCheckLatestView()
                binding.qrCodeViewRoot.isVisible -> hideQrCodeView()
                binding.bookmarkManagerRoot.isVisible -> hideBookmarkManager()
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

    private fun navigateToAddress() {
        val raw = binding.addressEdit.text?.toString().orEmpty()
        val navigable = BrowserPreferences.formatNavigableUrl(raw)
        currentUrl = navigable
        BrowserPreferences.persistUrl(this, navigable)
        webView?.loadUrl(navigable)
        hideMenuOverlay()
    }

    private fun loadUrlFromIntent(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val navigable = BrowserPreferences.formatNavigableUrl(trimmed)
        currentUrl = navigable
        BrowserPreferences.persistUrl(this, navigable)
        binding.addressEdit.setText(navigable)
        binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
        webView?.loadUrl(navigable) ?: binding.webView.loadUrl(navigable)
        hideBookmarkManager()
        hideCheckLatestView()
        hideQrCodeView()
        hideMenuOverlay()
        showMenuButtonTemporarily()
    }

    private fun updateNavigationButtons() {
        binding.buttonBack.isEnabled = webView?.canGoBack() == true
        binding.buttonForward.isEnabled = webView?.canGoForward() == true
    }

    private fun updateProgress(progress: Int) {
        val indicator = binding.progressIndicator
        if (progress in 1..99) {
            if (!indicator.isVisible) {
                indicator.visibility = View.VISIBLE
            }
            indicator.setProgressCompat(progress, true)
        } else {
            indicator.visibility = View.GONE
        }
    }

    private fun showMenuOverlay() {
        hideBookmarkManager()
        hideCheckLatestView()
        hideQrCodeView()
        binding.menuOverlay.visibility = View.VISIBLE
        binding.menuFab.hide()
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)

        // Material Design Expressive - spring animation with overshoot
        binding.menuCard.translationY = binding.menuCard.height.toFloat() + 100f
        binding.menuCard.alpha = 0f
        binding.menuCard.scaleX = 0.95f
        binding.menuCard.scaleY = 0.95f
        binding.menuOverlayScrim.alpha = 0f

        // Expressive entrance with spring-like overshoot
        binding.menuCard.animate()
            .translationY(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()

        binding.menuOverlayScrim.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        refreshBookmarks()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupMenuSwipeToClose() {
        var startY = 0f
        var startTranslationY = 0f
        val swipeThreshold = resources.displayMetrics.density * 100 // 100dp threshold

        binding.menuCard.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTranslationY = view.translationY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) { // Only allow downward swipe
                        view.translationY = startTranslationY + deltaY
                        // Fade scrim based on swipe progress
                        val progress = (deltaY / view.height).coerceIn(0f, 1f)
                        binding.menuOverlayScrim.alpha = 1f - progress
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > swipeThreshold) {
                        // Swipe exceeded threshold - close the menu
                        hideMenuOverlay()
                    } else {
                        // Snap back to original position
                        view.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
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

    private fun hideMenuOverlay() {
            if (binding.menuOverlay.visibility == View.VISIBLE) {
            hideKeyboard(binding.addressEdit)

            // Animate menu card sliding down
            binding.menuCard.animate()
                .translationY(binding.menuCard.height.toFloat())
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator(2f))
                .withEndAction {
                    binding.menuOverlay.visibility = View.GONE
                    hideBookmarkManager()
                    hideCheckLatestView()
                    hideQrCodeView()
                }
                .start()

            binding.menuOverlayScrim.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            showMenuButtonTemporarily()
        }
    }

    private fun showMenuButtonTemporarily() {
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        if (isInFullscreen()) {
            binding.menuFab.hide()
            return
        }
        binding.menuFab.hide()
        handler.postDelayed(showMenuFabRunnable, MENU_BUTTON_SHOW_DELAY_MS)
    }

    private fun openUriExternally(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(this, R.string.error_open_external, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
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
        val scheme = data.scheme?.lowercase() ?: return null
        return if (scheme == "http" || scheme == "https") data.toString() else null
    }

    private fun isInFullscreen(): Boolean = customView != null

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        (view.parent as? ViewGroup)?.removeView(view)

        customView = view
        customViewCallback = callback

        if (binding.menuOverlay.isVisible) {
            binding.menuOverlay.visibility = View.GONE
            hideKeyboard(binding.addressEdit)
        }

        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        binding.menuFab.hide()
        binding.webView.visibility = View.INVISIBLE
        binding.progressIndicator.visibility = View.GONE

        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            bringToFront()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.fullscreenContainer).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitFullscreen(fromWebChrome: Boolean = false) {
        if (customView == null) return

        binding.fullscreenContainer.apply {
            removeAllViews()
            visibility = View.GONE
        }

        binding.webView.visibility = View.VISIBLE

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())

        val callback = customViewCallback
        customView = null
        customViewCallback = null

        if (!fromWebChrome) {
            callback?.onCustomViewHidden()
        }

        showMenuButtonTemporarily()
    }

    private fun addBookmarkForCurrentPage() {
        val url = currentUrl.trim()
        if (url.isEmpty()) return
        val added = BrowserPreferences.addBookmark(this, url)
        if (added) {
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
            val emptyView = MaterialTextView(this).apply {
                text = getString(R.string.menu_bookmark_empty)
                setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
                gravity = android.view.Gravity.CENTER
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTextColor(getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium))
            }
            container.addView(emptyView)
            return
        }

        bookmarks.forEachIndexed { index, bookmark ->
            // Create card-like container for each bookmark
            val itemCard = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = (8 * density).toInt()
                    }
                }
                radius = (12 * density)
                cardElevation = 0f
                strokeWidth = (1 * density).toInt()
                strokeColor = com.google.android.material.R.attr.colorOutlineVariant.let { attr ->
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(attr, typedValue, true)
                    typedValue.data
                }
                setCardBackgroundColor(com.google.android.material.R.attr.colorSurfaceContainer.let { attr ->
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(attr, typedValue, true)
                    typedValue.data
                })
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    loadUrlFromIntent(bookmark)
                    hideBookmarkManager()
                }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding((12 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
            }

            // Bookmark icon
            val iconView = android.widget.ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (40 * density).toInt(),
                    (40 * density).toInt()
                )
                setImageResource(android.R.drawable.star_on)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    com.google.android.material.R.attr.colorPrimary.let { attr ->
                        val typedValue = android.util.TypedValue()
                        theme.resolveAttribute(attr, typedValue, true)
                        typedValue.data
                    }
                )
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(com.google.android.material.R.attr.colorPrimaryContainer.let { attr ->
                        val typedValue = android.util.TypedValue()
                        theme.resolveAttribute(attr, typedValue, true)
                        typedValue.data
                    })
                }
            }

            // Text container
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = (12 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
            }

            // Extract domain for title display
            val domain = try {
                java.net.URI(bookmark).host ?: bookmark
            } catch (e: Exception) {
                bookmark
            }

            val titleView = MaterialTextView(this).apply {
                text = domain
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }

            val urlView = MaterialTextView(this).apply {
                text = bookmark
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(com.google.android.material.R.attr.colorOnSurfaceVariant.let { attr ->
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(attr, typedValue, true)
                    typedValue.data
                })
            }

            textContainer.addView(titleView)
            textContainer.addView(urlView)

            // Delete button as icon button
            val deleteButton = MaterialButton(
                ContextThemeWrapper(
                    this,
                    com.google.android.material.R.style.Widget_Material3_Button_IconButton_Filled_Tonal
                )
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (40 * density).toInt(),
                    (40 * density).toInt()
                )
                setIconResource(android.R.drawable.ic_menu_delete)
                iconSize = (20 * density).toInt()
                iconTint = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.WHITE
                )
                setBackgroundColor(com.google.android.material.R.attr.colorError.let { attr ->
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(attr, typedValue, true)
                    typedValue.data
                })
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 0
                contentDescription = getString(R.string.bookmark_delete)
                setOnClickListener { removeBookmark(bookmark) }
            }

            row.addView(iconView)
            row.addView(textContainer)
            row.addView(deleteButton)
            itemCard.addView(row)
            container.addView(itemCard)
        }
    }

    private fun showBookmarkManager() {
        hideKeyboard(binding.addressEdit)
        binding.menuScroll.visibility = View.GONE
        binding.menuContentRoot.visibility = View.VISIBLE
        binding.bookmarkManagerRoot.visibility = View.VISIBLE
        refreshBookmarks()
    }

    private fun hideBookmarkManager() {
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        binding.menuContentRoot.visibility = View.VISIBLE
    }

    private fun showQrCodeView() {
        val url = currentUrl.trim()
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.qr_code_error, Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard(binding.addressEdit)
        binding.menuScroll.visibility = View.GONE
        binding.menuContentRoot.visibility = View.VISIBLE
        binding.qrCodeViewRoot.visibility = View.VISIBLE

        // Generate and display QR code
        val qrBitmap = generateQrCode(url)
        if (qrBitmap != null) {
            binding.qrCodeImage.setImageBitmap(qrBitmap)
            binding.qrCodeUrl.text = url
        } else {
            Toast.makeText(this, R.string.qr_code_error, Toast.LENGTH_SHORT).show()
            hideQrCodeView()
        }
    }

    private fun hideQrCodeView() {
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        binding.menuContentRoot.visibility = View.VISIBLE
    }

    private fun showCheckLatestView() {
        hideKeyboard(binding.addressEdit)
        binding.menuScroll.visibility = View.GONE
        binding.menuContentRoot.visibility = View.VISIBLE
        binding.checkLatestViewRoot.visibility = View.VISIBLE

        // Set installed version
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = pInfo.versionName ?: ""
            binding.checkLatestInstalledVersion.text = getString(R.string.installed_version_label, "v$versionName")
        } catch (_: Exception) {
        }

        // Check for latest version
        binding.checkLatestProgressIndicator.visibility = View.VISIBLE
        binding.checkLatestLatestVersion.text = getString(R.string.menu_checking_latest)

        Thread {
            val api = "https://api.github.com/repos/kododake/AABrowser/releases/latest"
            var latestTag = "unknown"
            try {
                val conn = java.net.URL(api).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val code = conn.responseCode
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(text)
                    latestTag = json.optString("tag_name", "unknown")
                    latestReleaseUrl = json.optString("html_url", latestReleaseUrl)
                }
            } catch (_: Exception) {
            }

            runOnUiThread {
                binding.checkLatestProgressIndicator.visibility = View.GONE
                binding.checkLatestLatestVersion.text = getString(R.string.latest_version_label, latestTag)
            }
        }.start()
    }

    private fun hideCheckLatestView() {
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        binding.menuContentRoot.visibility = View.VISIBLE
    }

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val size = 512 // QR code size in pixels
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MENU_BUTTON_AUTO_HIDE_DELAY_MS = 3000L
        private const val MENU_BUTTON_SHOW_DELAY_MS = 500L
        private const val GITHUB_REPO_URL = "https://github.com/kododake/AABrowser"
    }
}