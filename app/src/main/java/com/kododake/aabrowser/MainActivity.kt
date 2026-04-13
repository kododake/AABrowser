package com.kododake.aabrowser

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.kododake.aabrowser.analytics.UmamiTracker
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.data.SiteIconCache
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.configureWebView
import com.kododake.aabrowser.web.releaseCompletely
import com.kododake.aabrowser.web.updateDesktopMode
import com.kododake.aabrowser.web.updatePageDarkening
import com.kododake.aabrowser.web.updateUserAgentProfile
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import android.widget.RadioGroup
import com.kododake.aabrowser.settings.SettingsViews
import org.woheller69.freeDroidWarn.R as FreeDroidWarnR

class MainActivity : AppCompatActivity() {
    private data class BrowserTab(
        val id: Long,
        val webView: android.webkit.WebView,
        val speechBridge: com.kododake.aabrowser.web.SpeechRecognitionBridge,
        var currentUrl: String = "",
        var currentTitle: String = ""
    )

    private lateinit var binding: ActivityMainBinding
    private val isDebugBuild: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideMenuFab = Runnable {
        if (!::binding.isInitialized) return@Runnable
        if (isShowingStartPage) return@Runnable
        if (BrowserPreferences.isQuickActionButtonAlwaysVisible(this)) return@Runnable
        binding.menuFab.hide()
    }
    private val showMenuFabRunnable = Runnable {
        if (!::binding.isInitialized) return@Runnable
        if (isInFullscreen() || binding.menuOverlay.isVisible) return@Runnable
        binding.menuFab.show()
        if (!isShowingStartPage && !BrowserPreferences.isQuickActionButtonAlwaysVisible(this)) {
            handler.postDelayed(autoHideMenuFab, MENU_BUTTON_AUTO_HIDE_DELAY_MS)
        }
    }
    private val pickStartPageBackgroundLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            handleStartPageBackgroundPicked(uri)
        }

    private var webView: android.webkit.WebView? = null
    private var currentUrl: String = ""
    private var currentPageTitle: String = ""
    private var currentUserAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_CHROME
    private val browserTabs = mutableListOf<BrowserTab>()
    private var activeTabId: Long? = null
    private var nextTabId: Long = 1L
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isShowingCleartextDialog: Boolean = false
    private var isShowingMicrophoneDialog: Boolean = false
    private var latestReleaseUrl: String = "https://github.com/kododake/AABrowser/releases"
    private val umamiTracker: UmamiTracker by lazy { UmamiTracker(applicationContext) }
    private var pendingPermissionRequest: android.webkit.PermissionRequest? = null
    private var pendingSpeechBridgeTabId: Long? = null
    private var shouldForceSessionRestore: Boolean = false
    private var isShowingStartPage: Boolean = false
    private var isSyncingAddressFields: Boolean = false
    private var isStartPagePhotoOnlyMode: Boolean = false
    private var loadedStartPageBackgroundUri: String? = null
    private var loadedStartPageBackgroundBitmap: Bitmap? = null
    private var cachedStartPageGradientSignature: Int = 0

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(BrowserPreferences.createScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        AppCompatDelegate.setDefaultNightMode(BrowserPreferences.getThemeMode(this).nightMode)
        super.onCreate(savedInstanceState)
        shouldForceSessionRestore = savedInstanceState != null
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        umamiTracker.trackEvent("app_open")

        val disp = this.display
        val best = disp?.supportedModes?.maxWithOrNull(compareBy({ it.refreshRate }, { it.physicalWidth.toLong() * it.physicalHeight }))
        best?.let { mode ->
            val attrs = window.attributes
            attrs.preferredDisplayModeId = mode.modeId
            window.attributes = attrs
        }

        setupUi()
        setupBackPressHandling()
        ensureNotificationPermissionIfNeeded()
        showFreeDroidWarnOnUpgradeMaterial()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractBrowsableUrl(intent)?.let { loadUrlFromIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        applyMenuHeaderColors()
        refreshHomePageMode()
        refreshBookmarks()
        refreshTabs()
        refreshStartPage()
        syncUserAgentProfile()
        applyPersistentAddressBarPreference()
        applyQuickActionButtonPreferences()
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
        loadedStartPageBackgroundBitmap?.recycle()
        loadedStartPageBackgroundBitmap = null
        loadedStartPageBackgroundUri = null
        browserTabs.forEach { tab ->
            tab.speechBridge.destroy()
            tab.webView.releaseCompletely()
        }
        binding.webViewContainer.removeAllViews()
        browserTabs.clear()
        webView = null
        super.onDestroy()
    }

    private val activeTab: BrowserTab?
        get() = browserTabs.firstOrNull { it.id == activeTabId }

    private fun activeTabIndex(): Int {
        val index = browserTabs.indexOfFirst { it.id == activeTabId }
        return if (index >= 0) index else 0
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }
    
    private fun resolveReadableTextColor(
        backgroundColor: Int,
        preferredColor: Int,
        fallbackColor: Int
    ): Int {
        val preferredContrast = ColorUtils.calculateContrast(preferredColor, backgroundColor)
        val fallbackContrast = ColorUtils.calculateContrast(fallbackColor, backgroundColor)
        return if (preferredContrast >= fallbackContrast) preferredColor else fallbackColor
    }

    private fun applyMenuHeaderColors() {
        val headerBackground = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow)
        val readableText = resolveReadableTextColor(
            backgroundColor = headerBackground,
            preferredColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface),
            fallbackColor = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
        )
        val readableTint = ColorStateList.valueOf(readableText)

        binding.menuTitle.setTextColor(readableText)
        binding.pageTitle.setTextColor(readableText)
        binding.bookmarkManagerTitle.setTextColor(readableText)
        binding.bookmarkManagerSubtitle.setTextColor(readableText)
        binding.tabManagerTitle.setTextColor(readableText)
        binding.tabManagerSubtitle.setTextColor(readableText)
        binding.checkLatestViewTitle.setTextColor(readableText)
        binding.checkLatestViewSubtitle.setTextColor(readableText)

        binding.buttonClose.setTextColor(readableText)
        binding.buttonClose.iconTint = readableTint
        binding.buttonBookmarkManagerBack.setTextColor(readableText)
        binding.buttonBookmarkManagerBack.iconTint = readableTint
        binding.buttonBookmarkManagerBack.strokeColor = readableTint
        binding.buttonTabManagerBack.setTextColor(readableText)
        binding.buttonTabManagerBack.iconTint = readableTint
        binding.buttonTabManagerBack.strokeColor = readableTint
        binding.buttonCheckLatestBack.setTextColor(readableText)
        binding.buttonCheckLatestBack.iconTint = readableTint
        binding.buttonCheckLatestBack.strokeColor = readableTint
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
    }

    private fun grantableWebPermissionResources(request: android.webkit.PermissionRequest): Array<String> {
        val allowed = setOf(
            android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE
        )
        return request.resources.filter { it in allowed }.toTypedArray()
    }

    private fun protectedMediaResources(request: android.webkit.PermissionRequest): Array<String> {
        return request.resources
            .filter { it == android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            .toTypedArray()
    }

    private fun denyAudioButAllowProtectedMediaIfPresent(request: android.webkit.PermissionRequest) {
        val protectedMedia = protectedMediaResources(request)
        if (protectedMedia.isNotEmpty()) {
            request.grant(protectedMedia)
        } else {
            request.deny()
        }
    }

    private fun continueWebPermissionRequest(request: android.webkit.PermissionRequest) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val grantable = grantableWebPermissionResources(request)
            if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
        } else {
            pendingPermissionRequest = request
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
        }
    }

    private fun handleWebPermissionRequest(request: android.webkit.PermissionRequest) {
        val grantable = grantableWebPermissionResources(request)
        if (grantable.isEmpty()) {
            request.deny()
            return
        }

        val origin = runCatching { request.origin }.getOrNull()
        val host = origin?.host?.lowercase()
        if (android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE !in grantable || BrowserPreferences.isHostAllowedMicrophone(this, host)) {
            continueWebPermissionRequest(request)
            return
        }

        if (isFinishing || isDestroyed || isShowingMicrophoneDialog) {
            denyAudioButAllowProtectedMediaIfPresent(request)
            return
        }

        showMicrophoneAccessDialog(
            origin = origin,
            onAllowOnce = { continueWebPermissionRequest(request) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(this, it) }
                continueWebPermissionRequest(request)
            },
            onCancel = { denyAudioButAllowProtectedMediaIfPresent(request) }
        )
    }

    private fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?) {
        val pageUri = runCatching { pageUrl?.let(Uri::parse) }.getOrNull()
        val host = pageUri?.host?.lowercase()
        pendingSpeechBridgeTabId = tabId
        if (BrowserPreferences.isHostAllowedMicrophone(this, host)) {
            continueSpeechRecognitionMicrophoneAccess()
            return
        }

        if (isFinishing || isDestroyed || isShowingMicrophoneDialog) {
            browserTabs.firstOrNull { it.id == tabId }?.speechBridge?.onPermissionResult(false)
            return
        }

        showMicrophoneAccessDialog(
            origin = pageUri,
            onAllowOnce = { continueSpeechRecognitionMicrophoneAccess() },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(this, it) }
                continueSpeechRecognitionMicrophoneAccess()
            },
            onCancel = {
                browserTabs.firstOrNull { it.id == tabId }?.speechBridge?.onPermissionResult(false)
                pendingSpeechBridgeTabId = null
            }
        )
    }

    private fun continueSpeechRecognitionMicrophoneAccess() {
        val targetTab = browserTabs.firstOrNull { it.id == pendingSpeechBridgeTabId }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            targetTab?.speechBridge?.onPermissionResult(true)
            pendingSpeechBridgeTabId = null
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
        }
    }

    private fun showMicrophoneAccessDialog(
        origin: Uri?,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val originLabel = origin?.host ?: origin?.toString() ?: getString(R.string.microphone_access_unknown_origin)
        showSitePermissionDialog(
            title = getString(R.string.microphone_access_title),
            message = getString(R.string.microphone_access_message),
            isMicrophoneDialog = true,
            hostLabel = getString(R.string.microphone_access_host_label),
            hostValue = originLabel,
            detailMessage = getString(R.string.microphone_access_detail),
            onAllowOnce = onAllowOnce,
            onAllowHost = if (origin?.host.isNullOrBlank()) null else onAllowHost,
            onCancel = onCancel
        )
    }

    private fun showSitePermissionDialog(
        title: String,
        message: String,
        isMicrophoneDialog: Boolean,
        hostLabel: String? = null,
        hostValue: String? = null,
        detailMessage: String? = null,
        onAllowOnce: () -> Unit,
        onAllowHost: (() -> Unit)?,
        onCancel: () -> Unit
    ) {
        val flagAccessor: () -> Boolean = { if (isMicrophoneDialog) isShowingMicrophoneDialog else isShowingCleartextDialog }
        val flagSetter: (Boolean) -> Unit = { showing ->
            if (isMicrophoneDialog) {
                isShowingMicrophoneDialog = showing
            } else {
                isShowingCleartextDialog = showing
            }
        }

        if (isFinishing || isDestroyed) {
            onCancel()
            return
        }
        if (flagAccessor()) return
        flagSetter(true)

        val view = layoutInflater.inflate(R.layout.dialog_cleartext_confirmation, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.cleartext_title)
        val messageView = view.findViewById<android.widget.TextView>(R.id.cleartext_message)
        val hostContainer = view.findViewById<android.view.View>(R.id.cleartext_host_container)
        val hostLabelView = view.findViewById<android.widget.TextView>(R.id.cleartext_host_label)
        val hostValueView = view.findViewById<android.widget.TextView>(R.id.cleartext_host_value)
        val detailView = view.findViewById<android.widget.TextView>(R.id.cleartext_detail)
        val cancelButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel_dialog)
        val allowOnceButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_once)
        val allowHostButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_allow_host)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        ).setView(view).create()

        titleView.text = title
        messageView.text = message
        if (!hostLabel.isNullOrBlank() && !hostValue.isNullOrBlank()) {
            hostContainer.visibility = View.VISIBLE
            hostLabelView.text = hostLabel
            hostValueView.text = hostValue
        } else {
            hostContainer.visibility = View.GONE
        }
        if (!detailMessage.isNullOrBlank()) {
            detailView.visibility = View.VISIBLE
            detailView.text = detailMessage
        } else {
            detailView.visibility = View.GONE
        }

        cancelButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onCancel()
        }
        allowOnceButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onAllowOnce()
        }
        if (onAllowHost != null) {
            allowHostButton.visibility = View.VISIBLE
            allowHostButton.setOnClickListener {
                try { dialog.dismiss() } catch (_: Exception) {}
                onAllowHost()
            }
        } else {
            allowHostButton.visibility = View.GONE
        }

        dialog.setOnDismissListener { flagSetter(false) }

        try {
            dialog.show()
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            flagSetter(false)
            onCancel()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                if (granted) {
                    val grantable = grantableWebPermissionResources(request)
                    if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
                } else {
                    denyAudioButAllowProtectedMediaIfPresent(request)
                }
            }

            val speechTab = browserTabs.firstOrNull { it.id == pendingSpeechBridgeTabId }
            if (speechTab?.speechBridge?.hasPendingPermissionRequest() == true) {
                speechTab.speechBridge.onPermissionResult(granted)
            }
            pendingSpeechBridgeTabId = null
        }
    }

    private fun showFreeDroidWarnOnUpgradeMaterial() {
        if (isFinishing || isDestroyed) return

        val appVersionCode = runCatching {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        }.getOrDefault(1)

        val prefManager = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
        val warnedVersionCode = prefManager.getInt(FREE_DROID_WARN_VERSION_KEY, 0)
        if (appVersionCode <= warnedVersionCode) return

        val view = layoutInflater.inflate(R.layout.dialog_free_droid_warn, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.free_droid_warn_title)
        val messageView = view.findViewById<android.widget.TextView>(R.id.free_droid_warn_message)
        titleView.text = getString(android.R.string.dialog_alert_title)
        messageView.text = getString(FreeDroidWarnR.string.dialog_Warning)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setView(view)
            .setNegativeButton(FreeDroidWarnR.string.dialog_more_info) { _, _ ->
                loadUrlFromIntent(KEEP_ANDROID_OPEN_URL)
            }
            .setNeutralButton(FreeDroidWarnR.string.solution) { _, _ ->
                loadUrlFromIntent(FREE_DROID_WARN_SOLUTIONS_URL)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefManager.edit().putInt(FREE_DROID_WARN_VERSION_KEY, appVersionCode).apply()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(
            resolveThemeColor(androidx.appcompat.R.attr.colorError)
        )
    }

    private fun initializeTabs(
        intentUrl: String?,
        homePageUrl: String?,
        lastVisitedUrl: String?,
        restoreTabsOnLaunch: Boolean,
        resumeLastPageOnLaunch: Boolean
    ) {
        val shouldRestoreSavedTabs = shouldForceSessionRestore ||
            (intentUrl == null && homePageUrl.isNullOrBlank() && restoreTabsOnLaunch)
        val savedTabs = if (shouldRestoreSavedTabs) {
            BrowserPreferences.getSavedTabSession(this)
        } else {
            emptyList()
        }

        when {
            intentUrl != null -> createBrowserTab(
                initialUrl = BrowserPreferences.formatNavigableUrl(intentUrl),
                activate = true
            )

            !homePageUrl.isNullOrBlank() -> createBrowserTab(
                initialUrl = homePageUrl,
                activate = true
            )

            savedTabs.isNotEmpty() -> {
                savedTabs.forEach { entry ->
                    createBrowserTab(
                        initialUrl = entry.url,
                        initialTitle = entry.title.orEmpty(),
                        activate = false
                    )
                }
                val targetIndex = BrowserPreferences.getSavedActiveTabIndex(this)
                    .coerceIn(0, browserTabs.lastIndex)
                switchToTab(browserTabs[targetIndex].id)
            }

            resumeLastPageOnLaunch && !lastVisitedUrl.isNullOrBlank() -> createBrowserTab(
                initialUrl = lastVisitedUrl,
                activate = true
            )

            else -> createBrowserTab(
                initialUrl = null,
                initialTitle = getString(R.string.tab_manager_blank_title),
                activate = true
            )
        }
    }

    private fun createBrowserTab(
        initialUrl: String?,
        initialTitle: String = "",
        activate: Boolean
    ): BrowserTab? {
        if (browserTabs.size >= BrowserPreferences.MAX_OPEN_TABS) {
            Toast.makeText(
                this,
                getString(R.string.tab_manager_max_tabs, BrowserPreferences.MAX_OPEN_TABS),
                Toast.LENGTH_SHORT
            ).show()
            refreshTabs()
            return null
        }

        val tabView = android.webkit.WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }

        lateinit var tab: BrowserTab
        val speechBridge = com.kododake.aabrowser.web.SpeechRecognitionBridge(tabView) { pageUrl ->
            requestSpeechRecognitionMicrophoneAccess(tab.id, pageUrl)
        }
        tab = BrowserTab(
            id = nextTabId++,
            webView = tabView,
            speechBridge = speechBridge,
            currentUrl = initialUrl.orEmpty(),
            currentTitle = initialTitle
        )

        configureWebView(
            webView = tabView,
            callbacks = buildBrowserCallbacks(tab),
            useDesktopMode = BrowserPreferences.shouldUseDesktopMode(this),
            userAgentProfile = currentUserAgentProfile,
            allowDarkPages = BrowserPreferences.isBetaForceDarkPagesEnabled(this)
        )

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                tabView,
                com.kododake.aabrowser.web.SpeechRecognitionBridge.BRIDGE_OBJECT_NAME,
                setOf("*")
            ) { webView, message, sourceOrigin, isMainFrame, _ ->
                speechBridge.handleWebMessage(
                    message = message,
                    sourceOrigin = sourceOrigin,
                    isMainFrame = isMainFrame,
                    currentPageUrl = webView.url
                )
            }
        }

        tabView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun openExternal(url: String) {
                if (url.isNullOrBlank()) return
                runOnUiThread { runCatching { openUriExternally(Uri.parse(url)) } }
            }
        }, "Android")

        tabView.setOnTouchListener { _, _ ->
            showMenuButtonTemporarily()
            false
        }
        tabView.onPause()

        binding.webViewContainer.addView(tabView)
        browserTabs.add(tab)

        if (activate) {
            switchToTab(tab.id)
        } else {
            persistTabSession()
            refreshTabs()
        }
        return tab
    }

    private fun buildBrowserCallbacks(tab: BrowserTab): BrowserCallbacks {
        return BrowserCallbacks(
            onUrlChange = { url ->
                runOnUiThread {
                    tab.currentUrl = url
                    BrowserPreferences.persistUrl(this, url)
                    prefetchSiteIcon(url)
                    persistTabSession()
                    if (tab.id != activeTabId) return@runOnUiThread

                    currentUrl = url
                    if (!isShowingStartPage && binding.addressEdit.text?.toString() != url) {
                        binding.addressEdit.setText(url)
                        binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
                    }
                    updateNavigationButtons()
                    if (!isShowingStartPage) {
                        updateConnectionSecurityIcon(url)
                    }
                    refreshStartPage()
                    refreshTabs()
                }
            },
            onTitleChange = { title ->
                runOnUiThread {
                    val resolvedTitle = title.orEmpty()
                    tab.currentTitle = resolvedTitle
                    persistTabSession()
                    if (tab.id == activeTabId) {
                        currentPageTitle = resolvedTitle
                        if (!isShowingStartPage) {
                            binding.pageTitle.text = resolvedTitle.ifBlank { displayTitleForTab(tab) }
                        }
                    }
                    refreshTabs()
                }
            },
            onFaviconReceived = { url, icon ->
                runOnUiThread {
                    SiteIconCache.cacheIcon(this, url, icon)
                    if (isShowingStartPage) {
                        refreshStartPage()
                    }
                    if (binding.bookmarkManagerRoot.isVisible) {
                        refreshBookmarks()
                    }
                    if (binding.tabManagerRoot.isVisible) {
                        refreshTabs()
                    }
                }
            },
            onProgressChange = { progress ->
                if (tab.id == activeTabId) {
                    runOnUiThread { updateProgress(progress) }
                }
            },
            onShowDownloadPrompt = { uri ->
                runOnUiThread { openUriExternally(uri) }
            },
            onCleartextNavigationRequested = { uri, allowOnce, allowHostPermanently, cancel ->
                runOnUiThread {
                    showCleartextNavigationDialog(uri, allowOnce, allowHostPermanently, cancel)
                }
            },
            onError = { _, description ->
                runOnUiThread {
                    if (isDebugBuild && tab.id == activeTabId) {
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
            },
            onPermissionRequest = { request ->
                runOnUiThread { handleWebPermissionRequest(request) }
            }
        )
    }

    private fun showCleartextNavigationDialog(
        uri: Uri,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val host = uri.host ?: uri.toString()
        showSitePermissionDialog(
            title = getString(R.string.cleartext_connection_title),
            message = getString(R.string.cleartext_connection_message, host),
            isMicrophoneDialog = false,
            onAllowOnce = onAllowOnce,
            onAllowHost = onAllowHost,
            onCancel = onCancel
        )
    }

    private fun createNewTab(activate: Boolean): BrowserTab? {
        val initialUrl = BrowserPreferences.getHomePageUrl(this)
        return createBrowserTab(
            initialUrl = initialUrl,
            initialTitle = if (initialUrl.isNullOrBlank()) {
                getString(R.string.tab_manager_blank_title)
            } else {
                ""
            },
            activate = activate
        )
    }

    private fun switchToTab(tabId: Long) {
        val selectedTab = browserTabs.firstOrNull { it.id == tabId } ?: return
        if (webView !== selectedTab.webView) {
            webView?.onPause()
        }

        activeTabId = selectedTab.id
        webView = selectedTab.webView

        browserTabs.forEach { tab ->
            tab.webView.visibility = if (tab.id == selectedTab.id) View.VISIBLE else View.GONE
        }
        selectedTab.webView.onResume()

        currentUrl = selectedTab.currentUrl
        currentPageTitle = selectedTab.currentTitle

        if (binding.addressEdit.text?.toString() != selectedTab.currentUrl) {
            binding.addressEdit.setText(selectedTab.currentUrl)
            binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
        }
        syncAddressFieldsFrom(binding.addressEdit)
        updateAddressClearButtons()

        if (selectedTab.currentUrl.isBlank()) {
            showStartPage()
        } else {
            hideStartPage()
            if (selectedTab.webView.url.isNullOrBlank()) {
                selectedTab.webView.loadUrl(selectedTab.currentUrl)
            } else {
                binding.pageTitle.text = selectedTab.currentTitle.ifBlank { displayTitleForTab(selectedTab) }
                updateConnectionSecurityIcon(selectedTab.currentUrl)
            }
        }

        persistTabSession()
        refreshTabs()
        updateNavigationButtons()
        applyPersistentAddressBarPreference()
    }

    private fun closeTab(tabId: Long) {
        val tabIndex = browserTabs.indexOfFirst { it.id == tabId }
        if (tabIndex < 0) return

        val removedTab = browserTabs.removeAt(tabIndex)
        if (pendingSpeechBridgeTabId == removedTab.id) {
            pendingSpeechBridgeTabId = null
        }
        removedTab.speechBridge.destroy()
        binding.webViewContainer.removeView(removedTab.webView)
        removedTab.webView.releaseCompletely()

        if (browserTabs.isEmpty()) {
            createNewTab(activate = true)
            return
        }

        if (activeTabId == removedTab.id) {
            val nextIndex = tabIndex.coerceAtMost(browserTabs.lastIndex)
            switchToTab(browserTabs[nextIndex].id)
        } else {
            persistTabSession()
            refreshTabs()
        }
    }

    private fun persistTabSession() {
        BrowserPreferences.persistTabSession(
            context = this,
            tabs = browserTabs.map { tab ->
                BrowserPreferences.TabSessionEntry(
                    url = tab.currentUrl.takeIf { it.isNotBlank() },
                    title = tab.currentTitle.takeIf { it.isNotBlank() }
                )
            },
            activeIndex = activeTabIndex()
        )
    }

    private fun refreshTabs() {
        val tabCount = browserTabs.size.coerceAtLeast(1)
        val tabsLabel = if (tabCount > 1) {
            "${getString(R.string.menu_tabs)} ($tabCount)"
        } else {
            getString(R.string.menu_tabs)
        }
        binding.buttonTabs.text = tabsLabel

        val canAddMoreTabs = browserTabs.size < BrowserPreferences.MAX_OPEN_TABS
        binding.buttonNewTab.isEnabled = canAddMoreTabs
        binding.buttonNewTab.alpha = if (canAddMoreTabs) 1f else 0.6f
        binding.buttonTabManagerAdd.isEnabled = canAddMoreTabs
        binding.buttonTabManagerAdd.alpha = if (canAddMoreTabs) 1f else 0.6f

        refreshTabManager()
    }

    private fun refreshTabManager() {
        val container = binding.tabManagerList
        val density = resources.displayMetrics.density
        container.removeAllViews()

        if (browserTabs.isEmpty()) {
            container.addView(MaterialTextView(this).apply {
                text = getString(R.string.tab_manager_empty)
                setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
                gravity = android.view.Gravity.CENTER
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            return
        }

        browserTabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            val cardBackgroundColor = resolveThemeColor(
                if (isActive) {
                    com.google.android.material.R.attr.colorPrimaryContainer
                } else {
                    com.google.android.material.R.attr.colorSurfaceContainer
                }
            )
            val primaryTextColor = resolveReadableTextColor(
                backgroundColor = cardBackgroundColor,
                preferredColor = resolveThemeColor(
                    if (isActive) {
                        com.google.android.material.R.attr.colorOnPrimaryContainer
                    } else {
                        com.google.android.material.R.attr.colorOnSurface
                    }
                ),
                fallbackColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
            )
            val secondaryTextColor = resolveReadableTextColor(
                backgroundColor = cardBackgroundColor,
                preferredColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
                fallbackColor = primaryTextColor
            )
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 12 * density
                setCardBackgroundColor(cardBackgroundColor)
                strokeWidth = (1 * density).toInt()
                strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
                setOnClickListener {
                    switchToTab(tab.id)
                    hideMenuOverlay()
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
            }
            row.addView(
                createSiteIconBadge(
                    url = tab.currentUrl.takeIf { isActiveWebsiteUrl(it) },
                    sizeDp = 40f,
                    cornerRadiusDp = 12f,
                    paddingDp = 6f,
                    backgroundColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
            )
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
            }
            if (isActive) {
                textContainer.addView(MaterialTextView(this).apply {
                    text = getString(R.string.tab_manager_active_badge)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                    setTextColor(primaryTextColor)
                })
            }
            textContainer.addView(MaterialTextView(this).apply {
                text = displayTitleForTab(tab)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTextColor(primaryTextColor)
            })
            textContainer.addView(MaterialTextView(this).apply {
                text = if (tab.currentUrl.isBlank()) {
                    getString(R.string.tab_manager_blank_subtitle)
                } else {
                    tab.currentUrl
                }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(secondaryTextColor)
                alpha = 0.7f
            })

            val closeButton = MaterialButton(
                ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_IconButton_Filled_Tonal)
            ).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                setIconResource(R.drawable.ic_close)
                iconPadding = 0
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                contentDescription = getString(R.string.tab_manager_close)
                setOnClickListener { closeTab(tab.id) }
            }

            row.addView(textContainer)
            row.addView(closeButton)
            card.addView(row)

            container.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (8 * density).toInt()
            })
        }
    }

    private fun displayTitleForTab(tab: BrowserTab): String {
        return when {
            tab.currentTitle.isNotBlank() -> tab.currentTitle
            tab.currentUrl.isNotBlank() -> displayTitleForUrl(tab.currentUrl)
            else -> getString(R.string.tab_manager_blank_title)
        }
    }

    private fun showTabManager() {
        binding.menuScroll.visibility = View.GONE
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.GONE
        binding.tabManagerRoot.visibility = View.VISIBLE
        refreshTabs()
    }

    private fun hideTabManager() {
        binding.tabManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun setupUi() {
        val intentUrl = extractBrowsableUrl(intent)
        val homePageUrl = BrowserPreferences.getHomePageUrl(this)
        val lastVisitedUrl = BrowserPreferences.getLastVisitedUrl(this)
        val restoreTabsOnLaunch = BrowserPreferences.shouldRestoreTabsOnLaunch(this)
        val resumeLastPageOnLaunch = BrowserPreferences.shouldResumeLastPageOnLaunch(this)
        currentUserAgentProfile = BrowserPreferences.getUserAgentProfile(this)

        binding.menuFab.hide()
        initializeTabs(
            intentUrl = intentUrl,
            homePageUrl = homePageUrl,
            lastVisitedUrl = lastVisitedUrl,
            restoreTabsOnLaunch = restoreTabsOnLaunch,
            resumeLastPageOnLaunch = resumeLastPageOnLaunch
        )

        binding.desktopSwitch.isChecked = BrowserPreferences.shouldUseDesktopMode(this)
        binding.desktopSwitch.setOnCheckedChangeListener { _, isChecked ->
            BrowserPreferences.setDesktopMode(this, isChecked)
            browserTabs.forEach { it.webView.updateDesktopMode(isChecked, currentUserAgentProfile) }
        }

        configureAddressField(
            editText = binding.addressEdit,
            clearButton = binding.buttonClearAddress,
            goButton = binding.buttonGo,
            closeMenuAfterNavigate = true
        )
        configureAddressField(
            editText = binding.persistentAddressEdit,
            clearButton = binding.persistentButtonClearAddress,
            goButton = binding.persistentButtonGo,
            closeMenuAfterNavigate = false
        )
        syncAddressFieldsFrom(binding.addressEdit)
        updateAddressClearButtons()

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
        val navButtons = listOf(
            binding.buttonBack, binding.buttonReload, binding.buttonForward,
            binding.buttonBookmarks, binding.buttonSettings, binding.buttonTabs,
            binding.buttonNewTab
        )

        navButtons.forEach { btn ->
            btn.isEnabled = true
            btn.isClickable = true
            btn.iconTint = tonalColorStateList
        }

        binding.buttonExternal.setOnClickListener { showQrCodeView() }
        binding.buttonExternalGithub.iconTint = null
        binding.buttonExternalGithub.setOnClickListener { openUriExternally(Uri.parse(GITHUB_REPO_URL)) }
        binding.buttonBookmarks.setOnClickListener { showBookmarkManager() }
        binding.buttonTabs.setOnClickListener { showTabManager() }
        binding.buttonNewTab.setOnClickListener {
            createNewTab(activate = true)
            hideMenuOverlay()
        }
        binding.buttonStartPage.setOnClickListener {
            showStartPage()
            hideMenuOverlay()
        }
        binding.buttonBookmarkManagerBack.setOnClickListener { hideBookmarkManager() }
        binding.buttonTabManagerBack.setOnClickListener { hideTabManager() }
        binding.buttonTabManagerAdd.setOnClickListener {
            createNewTab(activate = true)
            hideMenuOverlay()
        }
        binding.buttonBookmarkAdd.setOnClickListener { addBookmarkForCurrentPage() }
        binding.buttonBookmarkStartPageAdd.setOnClickListener { addCurrentPageToStartPage() }
        binding.buttonBookmarkSetHomePage.setOnClickListener { setCurrentPageAsHomePage() }
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
        binding.buttonStartPageResume.setOnClickListener {
            val resumeUrl = BrowserPreferences.getLastVisitedUrl(this)
            if (resumeUrl.isNullOrBlank()) {
                Toast.makeText(this, R.string.start_page_no_last_page, Toast.LENGTH_SHORT).show()
            } else {
                loadUrlFromIntent(resumeUrl)
            }
        }
        binding.buttonStartPagePhotoOnly.setOnClickListener {
            isStartPagePhotoOnlyMode = !isStartPagePhotoOnlyMode
            applyStartPagePhotoOnlyMode()
            if (isStartPagePhotoOnlyMode) {
                Toast.makeText(this, R.string.start_page_photo_only_hint, Toast.LENGTH_SHORT).show()
            }
        }
        binding.startPageRoot.setOnClickListener {
            if (isStartPagePhotoOnlyMode) {
                isStartPagePhotoOnlyMode = false
                applyStartPagePhotoOnlyMode()
            }
        }

        fun applyStartPageDonateTab(isGithub: Boolean) {
            if (isGithub) {
                binding.startPageDonateAddress.text = START_PAGE_SPONSOR_URL
                val qrBitmap = generateQrCode(START_PAGE_SPONSOR_URL)
                if (qrBitmap != null) {
                    binding.startPageDonateQrImage.setImageBitmap(qrBitmap)
                } else {
                    binding.startPageDonateQrImage.setImageResource(R.drawable.ic_github)
                }
                binding.startPageDonateActionButton.text = getString(R.string.settings_donate_open_github_sponsors)
                binding.startPageDonateActionButton.setIconResource(R.drawable.favorite_24px)
                binding.startPageDonateActionButton.iconTint = ColorStateList.valueOf(Color.parseColor("#EC407A"))
                binding.startPageDonateActionButton.setOnClickListener {
                    openUriExternally(Uri.parse(START_PAGE_SPONSOR_URL))
                }
            } else {
                binding.startPageDonateAddress.text = getString(R.string.donate_bitcoin_address_value)
                binding.startPageDonateQrImage.setImageResource(R.drawable.bitcoin_qr)
                binding.startPageDonateActionButton.text = getString(R.string.donate_copy)
                binding.startPageDonateActionButton.setIconResource(R.drawable.content_copy_24px)
                binding.startPageDonateActionButton.iconTint = ColorStateList.valueOf(
                    resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                binding.startPageDonateActionButton.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Bitcoin Address", binding.startPageDonateAddress.text.toString()))
                    Toast.makeText(this, R.string.donate_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.startPageDonateTabGroup.check(binding.startPageDonateTabGithub.id)
        applyStartPageDonateTab(isGithub = true)
        binding.startPageDonateTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            applyStartPageDonateTab(isGithub = checkedId == binding.startPageDonateTabGithub.id)
        }

        binding.persistentButtonMenu.setOnClickListener { showMenuOverlay() }
        binding.menuFab.setOnClickListener { handleQuickActionButtonPressed() }
        binding.buttonClose.setOnClickListener { hideMenuOverlay() }
        binding.menuOverlayScrim.setOnClickListener { hideMenuOverlay() }
        applyMenuHeaderColors()

        setupManualDragLogic()

        updateNavigationButtons()
        refreshTabs()
        refreshStartPage()
        showMenuButtonTemporarily()
        refreshBookmarks()
        refreshHomePageMode()
        applyPersistentAddressBarPreference()
        applyQuickActionButtonPreferences()

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.menuVersion.text = getString(R.string.installed_version_label, "v${pInfo.versionName}")
        } catch (_: Exception) {}
    }

    private fun configureAddressField(
        editText: com.google.android.material.textfield.TextInputEditText,
        clearButton: MaterialButton,
        goButton: MaterialButton,
        closeMenuAfterNavigate: Boolean
    ) {
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateToAddress(editText.text?.toString().orEmpty(), closeMenuAfterNavigate)
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
            navigateToAddress(editText.text?.toString().orEmpty(), closeMenuAfterNavigate)
        }
    }

    private fun syncAddressFieldsFrom(
        source: com.google.android.material.textfield.TextInputEditText
    ) {
        if (isSyncingAddressFields) return
        val text = source.text?.toString().orEmpty()
        isSyncingAddressFields = true
        try {
            val peers = listOf(binding.addressEdit, binding.persistentAddressEdit)
            peers.filter { it !== source }.forEach { peer ->
                if (peer.text?.toString() != text) {
                    peer.setText(text)
                    if (peer.hasFocus()) {
                        peer.setSelection(peer.text?.length ?: 0)
                    }
                }
            }
        } finally {
            isSyncingAddressFields = false
        }
    }

    private fun updateAddressClearButtons() {
        updateAddressClearButton(binding.buttonClearAddress, !binding.addressEdit.text.isNullOrEmpty())
        updateAddressClearButton(
            binding.persistentButtonClearAddress,
            !binding.persistentAddressEdit.text.isNullOrEmpty()
        )
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

    private fun handleQuickActionButtonPressed() {
        when (BrowserPreferences.getQuickActionButtonMode(this)) {
            QuickActionButtonMode.MENU -> showMenuOverlay()
            QuickActionButtonMode.ADDRESS_BAR -> showMenuOverlay(focusAddressBar = true)
        }
    }

    private fun applyPersistentAddressBarPreference() {
        val shouldShow = BrowserPreferences.shouldAlwaysShowUrlBar(this) && !isInFullscreen()
        binding.persistentAddressBarCard.isVisible = shouldShow

        val extraTopPadding = if (shouldShow) persistentAddressBarHeightPx() else 0
        browserTabs.forEach { tab ->
            tab.webView.setPadding(0, extraTopPadding, 0, 0)
        }

        val startPagePadding = (24 * resources.displayMetrics.density).toInt()
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
        val density = resources.displayMetrics.density
        return (76 * density).toInt()
    }

    private fun applyQuickActionButtonPreferences() {
        if (!::binding.isInitialized) return

        val mode = BrowserPreferences.getQuickActionButtonMode(this)
        binding.menuFab.setImageResource(
            if (mode == QuickActionButtonMode.ADDRESS_BAR) {
                R.drawable.search_24px
            } else {
                android.R.drawable.ic_menu_more
            }
        )
        binding.menuFab.contentDescription = getString(
            if (mode == QuickActionButtonMode.ADDRESS_BAR) {
                R.string.menu_open_address_bar
            } else {
                R.string.menu_open_description
            }
        )

        val density = resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val position = BrowserPreferences.getQuickActionButtonPosition(this)
        val layoutParams = binding.menuFab.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.gravity = when (position) {
            QuickActionButtonPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            QuickActionButtonPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
            QuickActionButtonPosition.TOP_LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            QuickActionButtonPosition.TOP_RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
        }

        val topOffset = if (position == QuickActionButtonPosition.TOP_LEFT || position == QuickActionButtonPosition.TOP_RIGHT) {
            margin + if (binding.persistentAddressBarCard.isVisible) persistentAddressBarHeightPx() else 0
        } else {
            margin
        }
        layoutParams.setMargins(margin, topOffset, margin, margin)
        binding.menuFab.layoutParams = layoutParams

        if (isShowingStartPage || BrowserPreferences.isQuickActionButtonAlwaysVisible(this)) {
            handler.removeCallbacks(showMenuFabRunnable)
            handler.removeCallbacks(autoHideMenuFab)
            if (!isInFullscreen() && !binding.menuOverlay.isVisible) {
                binding.menuFab.show()
            }
        }
    }

    private fun focusMenuAddressBar() {
        binding.addressEdit.requestFocus()
        binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
        showKeyboard(binding.addressEdit)
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
                binding.tabManagerRoot.isVisible -> hideTabManager()
                binding.bookmarkManagerRoot.isVisible -> hideBookmarkManager()
                binding.settingsViewRoot.isVisible -> hideSettingsView()
                binding.menuOverlay.isVisible -> hideMenuOverlay()
                isShowingStartPage && currentUrl.isNotBlank() -> hideStartPage()
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
        browserTabs.forEach { tab ->
            tab.webView.updateUserAgentProfile(latestProfile, BrowserPreferences.shouldUseDesktopMode(this))
        }
    }

    private fun ensureActiveTab(): BrowserTab {
        return activeTab ?: createNewTab(activate = true)
            ?: error("Unable to create a browser tab")
    }

    private fun navigateActiveTabTo(navigable: String, closeMenuAfterNavigate: Boolean) {
        val targetTab = ensureActiveTab()
        val targetWebView = targetTab.webView
        val uri = runCatching { Uri.parse(navigable) }.getOrNull() ?: return

        fun finishNavigation(loadAction: () -> Unit) {
            targetTab.currentUrl = navigable
            targetTab.currentTitle = ""
            if (targetTab.id == activeTabId) {
                currentUrl = navigable
                currentPageTitle = ""
                if (binding.addressEdit.text?.toString() != navigable) {
                    binding.addressEdit.setText(navigable)
                    binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
                }
            }
            BrowserPreferences.persistUrl(this, navigable)
            persistTabSession()
            hideStartPage()
            loadAction()
            if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                hideMenuOverlay()
            } else {
                hideKeyboard(binding.persistentAddressEdit)
                binding.persistentAddressEdit.clearFocus()
            }
        }

        if (uri.scheme?.lowercase() == "http") {
            val host = uri.host?.lowercase()
            if (!BrowserPreferences.isHostAllowedCleartext(this, host)) {
                showCleartextNavigationDialog(
                    uri = uri,
                    onAllowOnce = {
                        finishNavigation {
                            targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                            targetWebView.post { targetWebView.loadUrl(navigable) }
                        }
                    },
                    onAllowHost = {
                        host?.let { BrowserPreferences.addAllowedCleartextHost(this, it) }
                        finishNavigation {
                            targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                            targetWebView.post { targetWebView.loadUrl(navigable) }
                        }
                    },
                    onCancel = {
                        if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                            hideMenuOverlay()
                        }
                    }
                )
                return
            }
        }

        finishNavigation { targetWebView.loadUrl(navigable) }
    }

    private fun navigateToAddress(raw: String, closeMenuAfterNavigate: Boolean) {
        val navigable = BrowserPreferences.formatNavigableUrl(raw)
        if (navigable.isEmpty()) return
        navigateActiveTabTo(navigable, closeMenuAfterNavigate)
    }

    private fun loadUrlFromIntent(rawUrl: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(rawUrl.trim())
        if (navigable.isEmpty()) return
        navigateActiveTabTo(navigable, closeMenuAfterNavigate = true)
    }

    private fun updateNavigationButtons() {
        val canInteractWithPage = !isShowingStartPage && currentUrl.isNotBlank()
        binding.buttonBack.isEnabled = !isShowingStartPage && webView?.canGoBack() == true
        binding.buttonForward.isEnabled = !isShowingStartPage && webView?.canGoForward() == true
        binding.buttonReload.isEnabled = canInteractWithPage
        binding.buttonExternal.isEnabled = canInteractWithPage
        binding.desktopSwitch.isEnabled = !isShowingStartPage
        binding.desktopSwitch.alpha = if (binding.desktopSwitch.isEnabled) 1f else 0.6f
    }

    private fun updateConnectionSecurityIcon(url: String?) {
        if (url.isNullOrBlank()) {
            binding.addressSecureIcon.visibility = View.GONE
            binding.addressInsecureIcon.visibility = View.GONE
            binding.persistentAddressSecureIcon.visibility = View.GONE
            binding.persistentAddressInsecureIcon.visibility = View.GONE
            return
        }
        val isSecure = try { url.lowercase().startsWith("https://") } catch (_: Exception) { false }
        binding.addressSecureIcon.visibility = if (isSecure) View.VISIBLE else View.GONE
        binding.addressInsecureIcon.visibility = if (isSecure) View.GONE else View.VISIBLE
        binding.persistentAddressSecureIcon.visibility = if (isSecure) View.VISIBLE else View.GONE
        binding.persistentAddressInsecureIcon.visibility = if (isSecure) View.GONE else View.VISIBLE
    }

    private fun updateProgress(progress: Int) {
        binding.progressIndicator.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
        if (progress in 1..99) binding.progressIndicator.setProgressCompat(progress, true)
    }

    private fun showMenuOverlay(focusAddressBar: Boolean = false) {
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
        handler.removeCallbacks(showMenuFabRunnable)
        handler.removeCallbacks(autoHideMenuFab)
        binding.menuFab.hide()
        refreshBookmarks()
        refreshTabs()
        refreshStartPage()
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
                hideTabManager()
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
        if (isShowingStartPage || BrowserPreferences.isQuickActionButtonAlwaysVisible(this)) {
            binding.menuFab.show()
            return
        }
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
        binding.persistentAddressBarCard.visibility = View.GONE
        webView?.visibility = View.INVISIBLE
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
        webView?.visibility = if (isShowingStartPage) View.INVISIBLE else View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        if (!fromWebChrome) callback?.onCustomViewHidden()
        applyPersistentAddressBarPreference()
        showMenuButtonTemporarily()
    }

    private fun addBookmarkForCurrentPage() {
        val url = currentUrl.trim()
        if (!isActiveWebsiteUrl(url) || isShowingStartPage) {
            Toast.makeText(this, R.string.start_page_add_current_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (BrowserPreferences.addBookmark(this, url)) {
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
        } else {
            Toast.makeText(this, R.string.bookmark_exists, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCurrentPageToStartPage() {
        val url = currentUrl.trim()
        if (isHomePageEnabled()) {
            Toast.makeText(this, R.string.start_page_add_disabled_by_home_page, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isActiveWebsiteUrl(url) || isShowingStartPage) {
            Toast.makeText(this, R.string.start_page_add_current_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        BrowserPreferences.addBookmark(this, url)
        showStartPageSlotPicker(url)
    }

    private fun setCurrentPageAsHomePage() {
        val url = currentUrl.trim()
        if (!isActiveWebsiteUrl(url) || isShowingStartPage) {
            Toast.makeText(this, R.string.home_page_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        BrowserPreferences.setHomePageUrl(this, url)
        Toast.makeText(this, R.string.home_page_set, Toast.LENGTH_SHORT).show()
        handleHomePagePreferenceChanged()
    }

    private fun removeBookmark(url: String) {
        if (BrowserPreferences.removeBookmark(this, url)) {
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            refreshBookmarks()
            refreshStartPage()
        }
    }

    private fun showStartPageSlotPicker(url: String) {
        if (isHomePageEnabled()) {
            Toast.makeText(this, R.string.start_page_add_disabled_by_home_page, Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = BrowserPreferences.formatNavigableUrl(url)
        val slots = BrowserPreferences.getStartPageSlots(this)
        val existingSlot = BrowserPreferences.findStartPageSlot(this, normalizedUrl)
        var selectedSlot = when {
            existingSlot >= 0 -> existingSlot
            else -> slots.indexOfFirst { it.isNullOrBlank() }.takeIf { it >= 0 } ?: 0
        }
        val slotLabels = Array(BrowserPreferences.MAX_START_PAGE_SITES) { index ->
            val slotLabel = getString(R.string.start_page_slot_number, index + 1)
            val slotUrl = slots.getOrNull(index)
            val summary = if (slotUrl.isNullOrBlank()) {
                getString(R.string.start_page_slot_empty_title)
            } else {
                displayLabelForUrl(slotUrl)
            }
            "$slotLabel - $summary"
        }

        val dialogBuilder = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(R.string.start_page_slot_picker_title)
            .setSingleChoiceItems(slotLabels, selectedSlot) { _, which ->
                selectedSlot = which
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.start_page_slot_picker_save) { _, _ ->
                BrowserPreferences.addBookmark(this, normalizedUrl)
                BrowserPreferences.setStartPageSlot(this, selectedSlot, normalizedUrl)
                refreshBookmarks()
                refreshStartPage()
                Toast.makeText(
                    this,
                    getString(R.string.start_page_slot_saved, getString(R.string.start_page_slot_number, selectedSlot + 1)),
                    Toast.LENGTH_SHORT
                ).show()
            }

        if (existingSlot >= 0) {
            dialogBuilder.setNeutralButton(R.string.start_page_slot_picker_remove) { _, _ ->
                BrowserPreferences.clearStartPageSlot(this, existingSlot)
                refreshBookmarks()
                refreshStartPage()
                Toast.makeText(this, R.string.start_page_slot_removed, Toast.LENGTH_SHORT).show()
            }
        }

        dialogBuilder.show()
    }

    private fun refreshBookmarks() {
        val container = binding.bookmarkManagerList
        val density = resources.displayMetrics.density
        val canUseCurrentPage = !isShowingStartPage && isActiveWebsiteUrl(currentUrl)
        val homePageEnabled = isHomePageEnabled()
        val currentHomePage = BrowserPreferences.getHomePageUrl(this)

        binding.buttonBookmarkAdd.isEnabled = canUseCurrentPage
        binding.buttonBookmarkAdd.alpha = if (canUseCurrentPage) 1f else 0.6f
        binding.buttonBookmarkStartPageAdd.isEnabled = canUseCurrentPage && !homePageEnabled
        binding.buttonBookmarkStartPageAdd.alpha = if (canUseCurrentPage && !homePageEnabled) 1f else 0.6f
        binding.buttonBookmarkSetHomePage.isEnabled = canUseCurrentPage
        binding.buttonBookmarkSetHomePage.alpha = if (canUseCurrentPage) 1f else 0.6f

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
            val startPageSlot = BrowserPreferences.findStartPageSlot(this, bookmark)
            val isHomePageBookmark = currentHomePage == bookmark
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
            row.addView(
                createSiteIconBadge(
                    url = bookmark,
                    sizeDp = 40f,
                    cornerRadiusDp = 12f,
                    paddingDp = 6f,
                    backgroundColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
            )
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (12 * density).toInt() }
            }
            textContainer.addView(MaterialTextView(this).apply {
                text = getString(R.string.start_page_slot_number, (startPageSlot + 1).coerceAtLeast(1))
                visibility = if (startPageSlot >= 0) View.VISIBLE else View.GONE
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            textContainer.addView(MaterialTextView(this).apply {
                text = displayLabelForUrl(bookmark)
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
            if (startPageSlot >= 0) {
                textContainer.addView(MaterialTextView(this).apply {
                    text = getString(
                        R.string.bookmark_start_page_badge,
                        getString(R.string.start_page_slot_number, startPageSlot + 1)
                    )
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    alpha = 0.8f
                })
            }
            if (isHomePageBookmark) {
                textContainer.addView(MaterialTextView(this).apply {
                    text = getString(R.string.bookmark_home_page_badge)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    alpha = 0.8f
                })
            }
            val pinBtn = MaterialButton(ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_IconButton_Filled_Tonal)).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
                    marginEnd = (8 * density).toInt()
                }
                setIconResource(R.drawable.kid_star_24px)
                iconPadding = 0
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                val backgroundTint = if (startPageSlot >= 0) {
                    resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
                } else {
                    resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                }
                val iconTintColor = if (startPageSlot >= 0) {
                    resolveThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
                } else {
                    resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                }
                backgroundTintList = ColorStateList.valueOf(backgroundTint)
                iconTint = ColorStateList.valueOf(iconTintColor)
                contentDescription = if (startPageSlot >= 0) {
                    getString(R.string.start_page_slot_picker_remove)
                } else {
                    getString(R.string.menu_start_page_add_current)
                }
                isEnabled = !homePageEnabled
                alpha = if (homePageEnabled) 0.5f else 1f
                setOnClickListener {
                    if (homePageEnabled) {
                        Toast.makeText(this@MainActivity, R.string.start_page_add_disabled_by_home_page, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (startPageSlot >= 0) {
                        BrowserPreferences.clearStartPageSlot(this@MainActivity, startPageSlot)
                        Toast.makeText(this@MainActivity, R.string.start_page_slot_removed, Toast.LENGTH_SHORT).show()
                        refreshBookmarks()
                        refreshStartPage()
                    } else {
                        showStartPageSlotPicker(bookmark)
                    }
                }
            }
            val delBtn = MaterialButton(ContextThemeWrapper(this, com.google.android.material.R.style.Widget_Material3_Button_IconButton_Filled_Tonal)).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                setIconResource(R.drawable.bookmark_remove_24px)
                setIconTint(ColorStateList.valueOf(Color.WHITE))
                iconPadding = 0
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                backgroundTintList = ColorStateList.valueOf(resolveThemeColor(android.R.attr.colorError))
                setOnClickListener { removeBookmark(bookmark) }
            }
            row.addView(textContainer)
            row.addView(pinBtn)
            row.addView(delBtn)
            itemCard.addView(row)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, (8 * density).toInt(), 0, 0)
            container.addView(itemCard, params)
        }
    }

    private fun isActiveWebsiteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    private fun displayLabelForUrl(url: String): String {
        return try {
            java.net.URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }

    private fun displayTitleForUrl(url: String): String {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
        val normalizedHost = host.removePrefix("www.").removePrefix("m.")
        val mappedTitle = when (normalizedHost) {
            "youtube.com" -> "YouTube"
            "google.com" -> "Google"
            "twitch.tv" -> "Twitch"
            "kick.com" -> "Kick"
            "wikipedia.org" -> "Wikipedia"
            "weather.com" -> "Weather"
            else -> null
        }
        if (mappedTitle != null) return mappedTitle

        val primarySegment = normalizedHost
            .substringBefore('.')
            .split('-', '_')
            .firstOrNull()
            .orEmpty()
        if (primarySegment.isBlank()) return displayLabelForUrl(url)

        return primarySegment.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    private fun prefetchSiteIcon(url: String?) {
        if (!isActiveWebsiteUrl(url)) return
        SiteIconCache.prefetchIconIfNeeded(this, url) { bitmap ->
            if (bitmap != null && isShowingStartPage) {
                refreshStartPage()
            }
        }
    }

    private fun resolveCachedSiteIcon(url: String?): Bitmap? {
        val cachedIcon = SiteIconCache.getCachedIcon(this, url)
        if (cachedIcon == null && !url.isNullOrBlank()) {
            prefetchSiteIcon(url)
        }
        return cachedIcon
    }

    private fun createSiteIconBadge(
        url: String?,
        sizeDp: Float,
        cornerRadiusDp: Float,
        paddingDp: Float,
        backgroundColor: Int,
        showAddOnEmptyUrl: Boolean = false
    ): View {
        val density = resources.displayMetrics.density
        val cachedIcon = resolveCachedSiteIcon(url)
        return FrameLayout(this).apply {
            val sizePx = (sizeDp * density).toInt()
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusDp * density
                setColor(backgroundColor)
            }

            addView(ImageView(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val paddingPx = (paddingDp * density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                if (cachedIcon != null) {
                    setImageBitmap(cachedIcon)
                } else {
                    setImageResource(R.drawable.public_24px)
                }
                visibility = if (showAddOnEmptyUrl && url.isNullOrBlank()) View.GONE else View.VISIBLE
            })

            if (showAddOnEmptyUrl) {
                addView(MaterialTextView(this@MainActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    gravity = android.view.Gravity.CENTER
                    text = "+"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    visibility = if (url.isNullOrBlank()) View.VISIBLE else View.GONE
                })
            }
        }
    }

    private fun isHomePageEnabled(): Boolean {
        return !BrowserPreferences.getHomePageUrl(this).isNullOrBlank()
    }

    private fun refreshHomePageMode() {
        binding.buttonStartPage.isVisible = !isHomePageEnabled()
    }

    private fun handleHomePagePreferenceChanged() {
        val homePageUrl = BrowserPreferences.getHomePageUrl(this)
        refreshHomePageMode()
        refreshBookmarks()
        refreshStartPage()
        rebuildSettingsContent()

        if (!homePageUrl.isNullOrBlank() && isShowingStartPage) {
            loadUrlFromIntent(homePageUrl)
        } else {
            updateNavigationButtons()
        }
    }

    private fun showBookmarkManager() {
        binding.menuScroll.visibility = View.GONE
        binding.tabManagerRoot.visibility = View.GONE
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.GONE
        binding.bookmarkManagerRoot.visibility = View.VISIBLE
        refreshBookmarks()
    }

    private fun hideBookmarkManager() {
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun showStartPage() {
        val homePageUrl = BrowserPreferences.getHomePageUrl(this)
        if (!homePageUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.start_page_disabled_by_home_page, Toast.LENGTH_SHORT).show()
            loadUrlFromIntent(homePageUrl)
            return
        }
        if (isInFullscreen()) exitFullscreen()
        isShowingStartPage = true
        isStartPagePhotoOnlyMode = false
        binding.startPageRoot.visibility = View.VISIBLE
        webView?.visibility = View.INVISIBLE
        binding.pageTitle.text = getString(R.string.start_page_title)
        binding.addressEdit.setText("")
        updateConnectionSecurityIcon(null)
        refreshStartPage()
        applyStartPagePhotoOnlyMode()
        updateNavigationButtons()
        showMenuButtonTemporarily()
    }

    private fun hideStartPage() {
        if (!isShowingStartPage && binding.startPageRoot.visibility != View.VISIBLE) return
        isShowingStartPage = false
        isStartPagePhotoOnlyMode = false
        applyStartPagePhotoOnlyMode()
        binding.startPageRoot.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        binding.pageTitle.text = currentPageTitle.ifBlank {
            currentUrl.takeIf { it.isNotBlank() }?.let(::displayLabelForUrl).orEmpty()
        }
        if (currentUrl.isNotBlank()) {
            if (binding.addressEdit.text?.toString() != currentUrl) {
                binding.addressEdit.setText(currentUrl)
                binding.addressEdit.setSelection(binding.addressEdit.text?.length ?: 0)
            }
        } else {
            binding.addressEdit.setText("")
        }
        updateConnectionSecurityIcon(currentUrl.takeIf { isActiveWebsiteUrl(it) })
        updateNavigationButtons()
        refreshBookmarks()
    }

    private fun applyStartPagePhotoOnlyMode() {
        if (!::binding.isInitialized) return
        binding.startPageScroll.visibility = if (isStartPagePhotoOnlyMode) View.GONE else View.VISIBLE
        binding.startPageDimOverlay.visibility = if (isStartPagePhotoOnlyMode) View.GONE else View.VISIBLE
        binding.buttonStartPagePhotoOnly.text = getString(
            if (isStartPagePhotoOnlyMode) R.string.start_page_show_ui else R.string.start_page_photo_only
        )

        if (isStartPagePhotoOnlyMode) {
            handler.removeCallbacks(showMenuFabRunnable)
            handler.removeCallbacks(autoHideMenuFab)
            binding.menuFab.hide()
        } else if (isShowingStartPage) {
            showMenuButtonTemporarily()
        }
    }

    private fun refreshStartPage() {
        refreshStartPageQuickLinks()
        refreshStartPageBackground()
        refreshStartPageResumeButton()
    }

    private fun refreshStartPageBackground() {
        applyDynamicStartPageGradientBackground()
        val backgroundUri = BrowserPreferences.getStartPageBackgroundUri(this)
        if (backgroundUri.isNullOrBlank()) {
            loadedStartPageBackgroundBitmap?.recycle()
            loadedStartPageBackgroundBitmap = null
            loadedStartPageBackgroundUri = null
            binding.startPageBackgroundImage.setImageBitmap(null)
            binding.startPageBackgroundImage.visibility = View.GONE
            return
        }

        if (backgroundUri == loadedStartPageBackgroundUri && loadedStartPageBackgroundBitmap != null) {
            binding.startPageBackgroundImage.setImageBitmap(loadedStartPageBackgroundBitmap)
            binding.startPageBackgroundImage.visibility = View.VISIBLE
            return
        }

        val reqWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val reqHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)

        val bitmap = decodeSampledBitmapFromUri(Uri.parse(backgroundUri), reqWidth, reqHeight)

        loadedStartPageBackgroundBitmap?.recycle()
        loadedStartPageBackgroundBitmap = bitmap
        loadedStartPageBackgroundUri = if (bitmap != null) backgroundUri else null

        if (bitmap != null) {
            binding.startPageBackgroundImage.setImageBitmap(bitmap)
            binding.startPageBackgroundImage.visibility = View.VISIBLE
        } else {
            binding.startPageBackgroundImage.setImageBitmap(null)
            binding.startPageBackgroundImage.visibility = View.GONE
        }
    }

    private fun applyDynamicStartPageGradientBackground() {
        val baseSurface = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val secondaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val tertiaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorTertiaryContainer)

        val signature = baseSurface xor primaryContainer xor secondaryContainer xor tertiaryContainer
        if (cachedStartPageGradientSignature == signature) return

        val linearStart = ColorUtils.blendARGB(baseSurface, secondaryContainer, 0.30f)
        val linearMid = ColorUtils.blendARGB(baseSurface, tertiaryContainer, 0.28f)
        val linearEnd = ColorUtils.blendARGB(baseSurface, primaryContainer, 0.30f)

        val ribbonA = ColorUtils.blendARGB(primaryContainer, tertiaryContainer, 0.45f)
        val ribbonB = ColorUtils.blendARGB(secondaryContainer, primaryContainer, 0.50f)
        val ribbonC = ColorUtils.blendARGB(tertiaryContainer, secondaryContainer, 0.42f)

        val baseLayer = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(linearStart, linearMid, linearEnd)
        ).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        val blobA = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = resources.displayMetrics.density * 460f
            setGradientCenter(0.18f, 0.22f)
            colors = intArrayOf(ColorUtils.setAlphaComponent(ribbonA, 170), Color.TRANSPARENT)
        }

        val blobB = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = resources.displayMetrics.density * 520f
            setGradientCenter(0.78f, 0.30f)
            colors = intArrayOf(ColorUtils.setAlphaComponent(ribbonB, 160), Color.TRANSPARENT)
        }

        val blobC = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = resources.displayMetrics.density * 540f
            setGradientCenter(0.55f, 0.82f)
            colors = intArrayOf(ColorUtils.setAlphaComponent(ribbonC, 150), Color.TRANSPARENT)
        }

        binding.startPageRoot.background = LayerDrawable(arrayOf(baseLayer, blobA, blobB, blobC))
        cachedStartPageGradientSignature = signature
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val hasBounds = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0
        }.getOrDefault(false)

        if (!hasBounds) return null

        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, reqWidth, reqHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun refreshStartPageQuickLinks() {
        val container = binding.startPageQuickLinksContainer
        val density = resources.displayMetrics.density
        container.removeAllViews()

        val slots = BrowserPreferences.getStartPageSlots(this)
        val rows = (BrowserPreferences.MAX_START_PAGE_SITES + 1) / 2
        repeat(rows) { rowIndex ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    if (rowIndex > 0) topMargin = (12 * density).toInt()
                }
            }

            repeat(2) { columnIndex ->
                val slotIndex = rowIndex * 2 + columnIndex
                if (slotIndex >= BrowserPreferences.MAX_START_PAGE_SITES) return@repeat
                val slotUrl = slots.getOrNull(slotIndex)
                row.addView(
                    createStartPageSlotCard(slotIndex, slotUrl).apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                            if (columnIndex == 0) {
                                marginEnd = (6 * density).toInt()
                            } else {
                                marginStart = (6 * density).toInt()
                            }
                        }
                    }
                )
            }

            container.addView(row)
        }
    }

    private fun createStartPageSlotCard(slotIndex: Int, url: String?): View {
        val density = resources.displayMetrics.density
        return com.google.android.material.card.MaterialCardView(this).apply {
            radius = 18 * density
            strokeWidth = (1 * density).toInt()
            strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
            setOnClickListener {
                if (url.isNullOrBlank()) {
                    showMenuOverlay()
                    showBookmarkManager()
                } else {
                    loadUrlFromIntent(url)
                }
            }

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())

                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(
                        createSiteIconBadge(
                            url = url,
                            sizeDp = 48f,
                            cornerRadiusDp = 14f,
                            paddingDp = 8f,
                            backgroundColor = resolveThemeColor(
                                if (url.isNullOrBlank()) {
                                    com.google.android.material.R.attr.colorSecondaryContainer
                                } else {
                                    com.google.android.material.R.attr.colorPrimaryContainer
                                }
                            ),
                            showAddOnEmptyUrl = true
                        )
                    )

                    addView(MaterialTextView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = (12 * density).toInt()
                        }
                        text = if (url.isNullOrBlank()) {
                            getString(R.string.start_page_slot_empty_title)
                        } else {
                            displayLabelForUrl(url)
                        }
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                        setTextColor(resolveThemeColor(androidx.appcompat.R.attr.colorPrimary))
                    })
                })

                addView(MaterialTextView(this@MainActivity).apply {
                    text = if (url.isNullOrBlank()) {
                        getString(R.string.start_page_slot_empty_title)
                    } else {
                        displayTitleForUrl(url)
                    }
                    setPadding(0, (12 * density).toInt(), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                })

                addView(MaterialTextView(this@MainActivity).apply {
                    text = if (url.isNullOrBlank()) {
                        getString(R.string.start_page_slot_empty_subtitle)
                    } else {
                        url
                    }
                    setPadding(0, (6 * density).toInt(), 0, 0)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
            })
        }
    }

    private fun refreshStartPageResumeButton() {
        binding.buttonStartPageResume.isVisible = !BrowserPreferences.getLastVisitedUrl(this).isNullOrBlank()
    }

    private fun handleStartPageBackgroundPicked(uri: Uri?) {
        if (uri == null) return

        val canReadImage = runCatching {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
        if (!canReadImage) {
            Toast.makeText(this, R.string.start_page_background_error, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val previousUri = BrowserPreferences.getStartPageBackgroundUri(this)
        BrowserPreferences.setStartPageBackgroundUri(this, uri.toString())
        if (!previousUri.isNullOrBlank() && previousUri != uri.toString()) {
            releaseStartPageBackgroundPermission(previousUri)
        }
        refreshStartPage()
        rebuildSettingsContent()
        Toast.makeText(this, R.string.start_page_background_set, Toast.LENGTH_SHORT).show()
    }

    private fun clearStartPageBackground() {
        val previousUri = BrowserPreferences.getStartPageBackgroundUri(this)
        if (previousUri.isNullOrBlank()) return
        releaseStartPageBackgroundPermission(previousUri)
        BrowserPreferences.clearStartPageBackgroundUri(this)
        refreshStartPage()
        rebuildSettingsContent()
        Toast.makeText(this, R.string.start_page_background_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun releaseStartPageBackgroundPermission(uriString: String) {
        runCatching {
            contentResolver.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun rebuildSettingsContent() {
        if (!::binding.isInitialized) return
        binding.settingsContentContainer.removeAllViews()
        if (binding.settingsViewRoot.isVisible) {
            ensureSettingsContentPopulated()
        }
    }

    private fun showQrCodeView() {
        val url = currentUrl.trim()
        if (url.isEmpty()) return
        binding.menuScroll.visibility = View.GONE
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.tabManagerRoot.visibility = View.GONE
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.GONE
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
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.tabManagerRoot.visibility = View.GONE
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.VISIBLE
        ensureSettingsContentPopulated()
    }

    private fun ensureSettingsContentPopulated() {
        if (binding.settingsContentContainer.childCount > 0) return
        try {
            val contentView = SettingsViews.createSettingsContent(
                context = this,
                includeDragHandle = false,
                callbacks = com.kododake.aabrowser.settings.SettingsCallbacks(
                    onClose = { hideSettingsView() },
                    onThemeChanged = { recreate() },
                    onPageDarkeningChanged = {
                        browserTabs.forEach { tab ->
                            tab.webView.updatePageDarkening(BrowserPreferences.isBetaForceDarkPagesEnabled(this))
                        }
                    },
                    onScaleChanged = { recreate() },
                    onHomePageChanged = { handleHomePagePreferenceChanged() },
                    onInAppControlsChanged = {
                        applyPersistentAddressBarPreference()
                        applyQuickActionButtonPreferences()
                    },
                    onPickStartPageBackground = {
                        pickStartPageBackgroundLauncher.launch(arrayOf("image/*"))
                    },
                    onClearStartPageBackground = { clearStartPageBackground() }
                )
            )
            binding.settingsContentContainer.addView(contentView)
        } catch (_: Exception) {}
    }

    private fun hideSettingsView() {
        binding.settingsViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }

    private fun showCheckLatestView() {
        binding.menuScroll.visibility = View.GONE
        binding.bookmarkManagerRoot.visibility = View.GONE
        binding.tabManagerRoot.visibility = View.GONE
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.settingsViewRoot.visibility = View.GONE
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
        private const val START_PAGE_SPONSOR_URL = "https://github.com/sponsors/kododake"
        private const val KEEP_ANDROID_OPEN_URL = "https://keepandroidopen.org"
        private const val FREE_DROID_WARN_SOLUTIONS_URL = "https://github.com/woheller69/FreeDroidWarn?tab=readme-ov-file#solutions"
        private const val FREE_DROID_WARN_VERSION_KEY = "versionCodeWarn"
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1101
        private const val REQUEST_CODE_RECORD_AUDIO = 1102
    }
}
