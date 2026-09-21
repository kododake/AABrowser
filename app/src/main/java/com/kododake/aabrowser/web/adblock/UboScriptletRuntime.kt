package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.R
import org.json.JSONArray
import org.json.JSONObject

/** Runs the upstream uBO scriptlet registry from domain-scoped +js() filters. */
object UboScriptletRuntime {
    private const val RUNTIME_ASSET = "adblock/ubo-scriptlets.js"
    private const val BRIDGE_NAME = "__aabrowserScriptletBridge"

    @Volatile
    private var cachedRuntime: String? = null

    fun install(webView: WebView, enabled: Boolean) {
        uninstall(webView)
        if (!enabled) return
        webView.addJavascriptInterface(InvocationBridge(), BRIDGE_NAME)
        webView.setTag(R.id.webview_ubo_scriptlet_bridge_tag, BRIDGE_NAME)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, source(webView.context), setOf("*"))
        }.getOrNull()?.let { handler ->
            webView.setTag(R.id.webview_ubo_scriptlet_handler_tag, handler)
        }
    }

    fun runFallbackIfNeeded(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        webView.evaluateJavascript(source(webView.context), null)
    }

    fun uninstall(webView: WebView) {
        runCatching {
            (webView.getTag(R.id.webview_ubo_scriptlet_handler_tag) as? androidx.webkit.ScriptHandler)?.remove()
        }
        webView.setTag(R.id.webview_ubo_scriptlet_handler_tag, null)
        val bridge = webView.getTag(R.id.webview_ubo_scriptlet_bridge_tag) as? String
        if (bridge != null) webView.removeJavascriptInterface(bridge)
        webView.setTag(R.id.webview_ubo_scriptlet_bridge_tag, null)
    }

    private fun source(context: Context): String {
        cachedRuntime?.let { return it }
        return synchronized(this) {
            cachedRuntime ?: context.applicationContext.assets.open(RUNTIME_ASSET)
                .bufferedReader().use { it.readText() }
                .also { cachedRuntime = it }
        }
    }

    private class InvocationBridge {
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
    }
}
