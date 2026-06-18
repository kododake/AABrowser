package com.kododake.aabrowser.tabs

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.R
import com.kododake.aabrowser.bookmarks.BookmarkManager
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import com.kododake.aabrowser.ui.adapters.TabAdapter
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.configureWebView
import com.kododake.aabrowser.web.releaseCompletely
import com.kododake.aabrowser.navigation.UrlSafetyCoordinator
import com.kododake.aabrowser.web.updateDesktopMode

data class BrowserTab(
    val id: Long,
    val webView: android.webkit.WebView,
    val speechBridge: com.kododake.aabrowser.web.SpeechRecognitionBridge,
    var currentUrl: String = "",
    var currentTitle: String = ""
)

class TabManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val bookmarkManager: BookmarkManager,
    private val callbacks: TabCallbacks
) {

    interface TabCallbacks {
        fun onTabChanged(tab: BrowserTab)
        fun buildBrowserCallbacks(tab: BrowserTab): BrowserCallbacks
        fun onNavigateToUrl(url: String)
        fun onShowStartPage()
        fun onHideStartPage()
        fun onShowMenuOverlay(focusAddressBar: Boolean = false)
        fun onHideMenuOverlay()
        fun resolveThemeColor(attrRes: Int): Int
        fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int
        fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?)
        fun onSpeechTabClosed(tabId: Long)
        fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): android.net.Uri?
        fun openUriExternally(uri: android.net.Uri)
        fun updateNavigationButtons()
        fun applyPersistentAddressBarPreference()
        fun syncAddressFieldsFrom(source: com.google.android.material.textfield.TextInputEditText)
        fun updateAddressClearButtons()
        fun updateConnectionSecurityIcon(url: String?)
        fun showMenuButtonTemporarily()
    }

    val browserTabs = mutableListOf<BrowserTab>()
    var activeTabId: Long? = null
    private var nextTabId: Long = 1L

    private val tabAdapter: TabAdapter by lazy {
        TabAdapter(
            bookmarkManager = bookmarkManager,
            activeTabIdProvider = { activeTabId },
            onTabClick = { tab ->
                switchToTab(tab.id)
                callbacks.onHideMenuOverlay()
            },
            onTabClose = { tab ->
                closeTab(tab.id) {
                    callbacks.onSpeechTabClosed(tab.id)
                }
            },
            onReordered = { newList ->
                browserTabs.clear()
                browserTabs.addAll(newList)
                persistTabSession()
            },
            resolveThemeColor = callbacks::resolveThemeColor,
            resolveReadableTextColor = callbacks::resolveReadableTextColor
        )
    }

    val activeTab: BrowserTab?
        get() {
            return browserTabs.firstOrNull { it.id == activeTabId }
        }

    fun initializeTabs(
        intentUrl: String?,
        homePageUrl: String?,
        lastVisitedUrl: String?,
        restoreTabsOnLaunch: Boolean,
        resumeLastPageOnLaunch: Boolean,
        shouldForceSessionRestore: Boolean
    ) {
        setupRecyclerView()
        
        val shouldRestoreSavedTabs = shouldForceSessionRestore ||
            (intentUrl == null && homePageUrl.isNullOrBlank() && restoreTabsOnLaunch)
            
        val savedTabs = if (shouldRestoreSavedTabs) {
            BrowserPreferences.getSavedTabSession(activity)
        } else {
            emptyList()
        }

        when {
            intentUrl != null -> {
                createBrowserTab(initialUrl = BrowserPreferences.formatNavigableUrl(intentUrl), activate = true)
            }
            !homePageUrl.isNullOrBlank() -> {
                createBrowserTab(initialUrl = homePageUrl, activate = true)
            }
            savedTabs.isNotEmpty() -> {
                savedTabs.forEach { entry ->
                    createBrowserTab(initialUrl = entry.url, initialTitle = entry.title.orEmpty(), activate = false)
                }
                val savedActiveIndex = BrowserPreferences.getSavedActiveTabIndex(activity)
                val targetIndex = savedActiveIndex.coerceIn(0, browserTabs.lastIndex)
                switchToTab(browserTabs[targetIndex].id)
            }
            resumeLastPageOnLaunch && !lastVisitedUrl.isNullOrBlank() -> {
                createBrowserTab(initialUrl = lastVisitedUrl, activate = true)
            }
            else -> {
                createBrowserTab(initialUrl = null, initialTitle = activity.getString(R.string.tab_manager_blank_title), activate = true)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.tabManagerList.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = tabAdapter
        }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                tabAdapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.tabManagerList)
    }

    fun createBrowserTab(initialUrl: String?, initialTitle: String = "", activate: Boolean): BrowserTab? {
        if (browserTabs.size >= BrowserPreferences.MAX_OPEN_TABS) {
            val message = activity.getString(R.string.tab_manager_max_tabs, BrowserPreferences.MAX_OPEN_TABS)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            refreshTabs()
            return null
        }

        val tabView = android.webkit.WebView(activity).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }

        lateinit var tab: BrowserTab
        val speechBridge = com.kododake.aabrowser.web.SpeechRecognitionBridge(tabView) { pageUrl ->
            callbacks.requestSpeechRecognitionMicrophoneAccess(tab.id, pageUrl)
        }
        
        tab = BrowserTab(
            id = nextTabId++,
            webView = tabView,
            speechBridge = speechBridge,
            currentUrl = initialUrl.orEmpty(),
            currentTitle = initialTitle
        )

        configureWebView(tabView, callbacks.buildBrowserCallbacks(tab), BrowserPreferences.shouldUseDesktopMode(activity), BrowserPreferences.getUserAgentProfile(activity), BrowserPreferences.isBetaForceDarkPagesEnabled(activity))
        setupWebMessageListener(tabView, speechBridge)
        setupJavascriptInterface(tabView)

        tabView.setOnTouchListener { _, _ ->
            callbacks.showMenuButtonTemporarily()
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

    private fun setupWebMessageListener(webView: android.webkit.WebView, speechBridge: com.kododake.aabrowser.web.SpeechRecognitionBridge) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, com.kododake.aabrowser.web.SpeechRecognitionBridge.BRIDGE_OBJECT_NAME, setOf("*")) { webViewInstance, message, sourceOrigin, isMainFrame, _ ->
                speechBridge.handleWebMessage(message, sourceOrigin, isMainFrame, webViewInstance.url)
            }
        }
    }

    private fun setupJavascriptInterface(webView: android.webkit.WebView) {
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun openExternal(url: String) {
                activity.runOnUiThread {
                    val safeUri = callbacks.sanitizeJsExternalUrl(webView, url)
                    if (safeUri != null) {
                        callbacks.openUriExternally(safeUri)
                    }
                }
            }
        }, "Android")
    }

    fun createNewTab(activate: Boolean): BrowserTab? {
        val initialUrl = BrowserPreferences.getHomePageUrl(activity)
        return createBrowserTab(initialUrl, if (initialUrl.isNullOrBlank()) activity.getString(R.string.tab_manager_blank_title) else "", activate)
    }

    fun switchToTab(tabId: Long) {
        val selectedTab = browserTabs.firstOrNull { it.id == tabId }
        if (selectedTab == null) {
            return
        }
        val currentActiveWebView = activeTab?.webView
        if (currentActiveWebView !== selectedTab.webView) {
            currentActiveWebView?.onPause()
        }

        activeTabId = selectedTab.id

        browserTabs.forEach { tab ->
            tab.webView.visibility = if (tab.id == selectedTab.id) View.VISIBLE else View.GONE
        }
        selectedTab.webView.onResume()

        if (binding.addressEdit.text?.toString() != selectedTab.currentUrl) {
            binding.addressEdit.setText(selectedTab.currentUrl)
            binding.addressEdit.setSelection(selectedTab.currentUrl.length)
        }
        
        callbacks.syncAddressFieldsFrom(binding.addressEdit)
        callbacks.updateAddressClearButtons()

        if (selectedTab.currentUrl.isBlank()) {
            callbacks.onShowStartPage()
        } else {
            callbacks.onHideStartPage()
            if (selectedTab.webView.url.isNullOrBlank()) {
                selectedTab.webView.loadUrl(selectedTab.currentUrl)
            } else {
                binding.pageTitle.text = selectedTab.currentTitle.ifBlank { displayTitleForTab(selectedTab) }
                callbacks.updateConnectionSecurityIcon(selectedTab.currentUrl)
            }
        }

        persistTabSession()
        refreshTabs()
        callbacks.updateNavigationButtons()
        callbacks.applyPersistentAddressBarPreference()
        callbacks.onTabChanged(selectedTab)
    }

    fun closeTab(tabId: Long, onSpeechTabClosed: () -> Unit) {
        val index = browserTabs.indexOfFirst { it.id == tabId }
        if (index < 0) {
            return
        }

        val removedTab = browserTabs.removeAt(index)
        onSpeechTabClosed()
        
        removedTab.speechBridge.destroy()
        binding.webViewContainer.removeView(removedTab.webView)
        removedTab.webView.releaseCompletely()

        if (browserTabs.isEmpty()) {
            createNewTab(activate = true)
            return
        }

        if (activeTabId == removedTab.id) {
            val nextIndex = index.coerceAtMost(browserTabs.lastIndex)
            switchToTab(browserTabs[nextIndex].id)
        } else {
            persistTabSession()
            refreshTabs()
        }
    }

    fun persistTabSession() {
        val entries = browserTabs.map { BrowserPreferences.TabSessionEntry(it.currentUrl.takeIf { u -> u.isNotBlank() }, it.currentTitle.takeIf { t -> t.isNotBlank() }) }
        val activeIndex = browserTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
        BrowserPreferences.persistTabSession(activity, entries, activeIndex)
    }

    fun followRedirectForActiveTab(nextLocation: String): Boolean {
        val tab = activeTab ?: return false
        val decision = UrlSafetyCoordinator.recordRedirectHop(tab.id, tab.currentUrl, nextLocation)
        return when (decision) {
            is UrlSafetyCoordinator.RedirectDecision.Allowed -> {
                tab.currentUrl = decision.url
                tab.webView.loadUrl(decision.url)
                true
            }
            is UrlSafetyCoordinator.RedirectDecision.Blocked -> false
        }
    }

    fun importSharedBookmarkBatch(urls: List<String>): Int {
        val current = BrowserPreferences.getBookmarks(activity)
        val merged = UrlSafetyCoordinator.mergeBookmarkLists(current, urls)
        BrowserPreferences.setBookmarks(activity, merged)
        refreshTabs()
        return merged.size - current.size
    }

    fun refreshTabs() {
        val count = browserTabs.size.coerceAtLeast(1)
        binding.buttonTabs.text = if (count > 1) "${activity.getString(R.string.menu_tabs)} ($count)" else activity.getString(R.string.menu_tabs)

        val canAdd = browserTabs.size < BrowserPreferences.MAX_OPEN_TABS
        listOf(binding.buttonNewTab, binding.buttonTabManagerAdd).forEach {
            it.isEnabled = canAdd
            it.alpha = if (canAdd) 1.0f else 0.6f
        }

        tabAdapter.submitList(browserTabs.toList())
    }

    fun displayTitleForTab(tab: BrowserTab): String {
        return when {
            tab.currentTitle.isNotBlank() -> tab.currentTitle
            tab.currentUrl.isNotBlank() -> bookmarkManager.displayTitleForUrl(tab.currentUrl)
            else -> activity.getString(R.string.tab_manager_blank_title)
        }
    }

    fun showTabManager() {
        listOf(binding.menuScroll, binding.bookmarkManagerRoot, binding.qrCodeViewRoot, binding.checkLatestViewRoot, binding.settingsViewRoot).forEach { it.visibility = View.GONE }
        binding.tabManagerRoot.visibility = View.VISIBLE
        refreshTabs()
    }

    fun hideTabManager() {
        binding.tabManagerRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
    }
}
