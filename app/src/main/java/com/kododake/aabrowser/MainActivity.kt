package com.kododake.aabrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            onError = { _, description ->
                runOnUiThread {
                    val message = description ?: getString(R.string.error_generic_message)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        binding.buttonClose.setOnClickListener { hideMenuOverlay() }

        binding.addressEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToAddress()
                true
            } else {
                false
            }
        }

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
            runCatching { Uri.parse(currentUrl) }
                .getOrNull()
                ?.let { openUriExternally(it) }
        }

        binding.buttonExternalGithub.setOnClickListener {
            openUriExternally(Uri.parse(GITHUB_REPO_URL))
        }

        binding.buttonBookmarks.setOnClickListener { showBookmarkManager() }
        binding.buttonBookmarkManagerBack.setOnClickListener { hideBookmarkManager() }
        binding.buttonBookmarkAdd.setOnClickListener { addBookmarkForCurrentPage() }

        updateNavigationButtons()
        showMenuButtonTemporarily()
        refreshBookmarks()
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                isInFullscreen() -> exitFullscreen()
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
        binding.menuOverlay.visibility = View.VISIBLE
        binding.menuFab.hide()
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        refreshBookmarks()
    }

    private fun hideMenuOverlay() {
        if (binding.menuOverlay.visibility == View.VISIBLE) {
            binding.menuOverlay.visibility = View.GONE
            hideKeyboard(binding.addressEdit)
            hideBookmarkManager()
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
                setPadding(0, (4 * density).toInt(), 0, 0)
            }
            container.addView(emptyView)
            return
        }

        bookmarks.forEachIndexed { index, bookmark ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = (8 * density).toInt()
                    }
                }
            }

            val openButton = MaterialButton(
                ContextThemeWrapper(
                    this,
                    com.google.android.material.R.style.Widget_Material3_Button_TextButton
                )
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = bookmark
                isAllCaps = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setOnClickListener {
                    loadUrlFromIntent(bookmark)
                    hideBookmarkManager()
                }
            }

            val deleteButton = MaterialButton(
                ContextThemeWrapper(
                    this,
                    com.google.android.material.R.style.Widget_Material3_Button_TextButton
                )
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * density).toInt()
                }
                text = getString(R.string.bookmark_delete)
                isAllCaps = false
                setOnClickListener { removeBookmark(bookmark) }
            }

            row.addView(openButton)
            row.addView(deleteButton)
            container.addView(row)
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

    companion object {
        private const val MENU_BUTTON_AUTO_HIDE_DELAY_MS = 3000L
        private const val MENU_BUTTON_SHOW_DELAY_MS = 500L
        private const val GITHUB_REPO_URL = "https://github.com/kododake/AABrowser"
    }
}