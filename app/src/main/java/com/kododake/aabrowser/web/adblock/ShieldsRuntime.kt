package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Installs the page-side half of Shields into a WebView: a document-start cosmetic filter script
 * (site-specific and generic element hiding) and the upstream uBO scriptlet registry, both fed by
 * a small synchronous JavaScript bridge into the immutable [FilterEngine] snapshot.
 *
 * Document-start scripts run in every frame before any page script, which is what lets hide rules
 * apply before the first paint and inside iframes. Bridge calls run on WebView's JavaBridge thread,
 * never on the UI thread, and only read the volatile engine snapshot, so they need no locking.
 */
object ShieldsRuntime {
    private const val SCRIPTLET_ASSET = "adblock/ubo-scriptlets.js"
    private const val COSMETIC_ASSET = "adblock/cosmetic-runtime.js"
    private const val BRIDGE_NAME = "__aabrowserScriptletBridge"

    private val assetCache = HashMap<String, String>()

    fun install(webView: WebView, enabled: Boolean) {
        uninstall(webView)
        if (!enabled) return
        webView.addJavascriptInterface(ShieldsBridge(), BRIDGE_NAME)
        webView.setTag(R.id.webview_ubo_scriptlet_bridge_tag, BRIDGE_NAME)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val context = webView.context
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, asset(context, COSMETIC_ASSET), setOf("*"))
        }.getOrNull()?.let { handler -> webView.setTag(R.id.webview_cosmetic_handler_tag, handler) }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, asset(context, SCRIPTLET_ASSET), setOf("*"))
        }.getOrNull()?.let { handler -> webView.setTag(R.id.webview_ubo_scriptlet_handler_tag, handler) }
    }

    /** Older WebViews without document-start scripts get the runtimes injected as early as possible. */
    fun runFallbackIfNeeded(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        webView.evaluateJavascript(asset(webView.context, COSMETIC_ASSET), null)
        webView.evaluateJavascript(asset(webView.context, SCRIPTLET_ASSET), null)
    }

    fun uninstall(webView: WebView) {
        for (tag in intArrayOf(R.id.webview_cosmetic_handler_tag, R.id.webview_ubo_scriptlet_handler_tag)) {
            runCatching { (webView.getTag(tag) as? ScriptHandler)?.remove() }
            webView.setTag(tag, null)
        }
        val bridge = webView.getTag(R.id.webview_ubo_scriptlet_bridge_tag) as? String
        if (bridge != null) webView.removeJavascriptInterface(bridge)
        webView.setTag(R.id.webview_ubo_scriptlet_bridge_tag, null)
    }

    private fun asset(context: Context, name: String): String {
        synchronized(assetCache) { assetCache[name]?.let { return it } }
        val source = context.applicationContext.assets.open(name).bufferedReader().use { it.readText() }
        synchronized(assetCache) { assetCache[name] = source }
        return source
    }

    /**
     * Exposed to every frame. It only reveals public filter-list data, never anything about the
     * user, and every method is cheap and bounded so a page cannot stall its own JavaScript thread.
     */
    private class ShieldsBridge {
        @JavascriptInterface
        fun getInvocations(pageUrl: String?): String {
            val array = JSONArray()
            AdBlocker.scriptletInvocations(pageUrl).forEach { invocation ->
                array.put(JSONObject().apply {
                    put("name", invocation.name)
                    put("arguments", JSONArray(invocation.arguments))
                    put("trusted", invocation.trusted)
                })
            }
            return array.toString()
        }

        @JavascriptInterface
        fun cosmeticInit(pageUrl: String?): String = AdBlocker.cosmeticInitJson(pageUrl)

        @JavascriptInterface
        fun genericSelectors(pageUrl: String?, classesJson: String?, idsJson: String?): String =
            AdBlocker.genericSelectorsJson(pageUrl, classesJson, idsJson)
    }
}
