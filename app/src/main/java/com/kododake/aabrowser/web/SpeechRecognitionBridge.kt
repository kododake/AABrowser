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

package com.kododake.aabrowser.web

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

class SpeechRecognitionBridge(
    webView: WebView,
    private val onRequestMicrophoneAccess: (String?) -> Unit
) : RecognitionListener {

    private enum class BridgeCommand {
        START,
        STOP,
        ABORT
    }

    private val webViewRef = WeakReference(webView)
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingLang: String? = null

    fun handleWebMessage(
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        currentPageUrl: String?
    ) {
        if (!isTrustedCaller(sourceOrigin, isMainFrame, currentPageUrl)) return

        val payload = message.data ?: return
        val command = parseCommand(payload) ?: return
        when (command.first) {
            BridgeCommand.START -> {
                pendingLang = command.second.orEmpty()
                webViewRef.get()?.post { onRequestMicrophoneAccess(sourceOrigin.toString()) }
            }

            BridgeCommand.STOP -> {
                webViewRef.get()?.post { speechRecognizer?.stopListening() }
            }

            BridgeCommand.ABORT -> {
                webViewRef.get()?.post {
                    speechRecognizer?.cancel()
                    stopInternal()
                    dispatchSimple("end")
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val lang = pendingLang
        pendingLang = null
        if (granted && lang != null) {
            startListening(lang)
        } else {
            dispatchError("not-allowed")
            dispatchSimple("end")
        }
    }

    fun destroy() {
        stopInternal()
    }

    fun hasPendingPermissionRequest(): Boolean = pendingLang != null

    private fun startListening(lang: String) {
        val webView = webViewRef.get() ?: return
        val context = webView.context

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            dispatchError("service-not-allowed")
            dispatchSimple("end")
            return
        }

        stopInternal()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                if (lang.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            }
            sr.startListening(intent)
        }
    }

    private fun stopInternal() {
        speechRecognizer?.apply {
            try { stopListening() } catch (_: Exception) {}
            try { destroy() } catch (_: Exception) {}
        }
        speechRecognizer = null
    }

    private fun isTrustedCaller(sourceOrigin: Uri, isMainFrame: Boolean, currentPageUrl: String?): Boolean {
        if (!isMainFrame) return false
        val currentPageOrigin = currentPageUrl
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() }
            ?: return false
        return normalizedOrigin(sourceOrigin) == normalizedOrigin(currentPageOrigin)
    }

    private fun normalizedOrigin(uri: Uri): String {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }
        return "$scheme://$host:$port"
    }

    private fun parseCommand(payload: String): Pair<BridgeCommand, String?>? {
        return try {
            val json = JSONObject(payload)
            val type = json.optString("type").lowercase()
            when (type) {
                "start" -> BridgeCommand.START to json.optString("lang")
                "stop" -> BridgeCommand.STOP to null
                "abort" -> BridgeCommand.ABORT to null
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun dispatchSimple(eventType: String) {
        val webView = webViewRef.get() ?: return
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('$eventType')", null
            )
        }
    }

    private fun dispatchError(errorCode: String) {
        val webView = webViewRef.get() ?: return
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('error','$errorCode')", null
            )
        }
    }

    private fun dispatchResults(
        matches: List<String>,
        confidences: FloatArray?,
        isFinal: Boolean
    ) {
        val webView = webViewRef.get() ?: return
        val alts = JSONArray()
        matches.forEachIndexed { i, text ->
            alts.put(JSONObject().apply {
                put("transcript", text)
                put("confidence", (confidences?.getOrNull(i) ?: 0.9f).toDouble())
            })
        }
        val payload = JSONObject().apply {
            put("a", alts)
            put("f", isFinal)
        }.toString()
        val escaped = payload
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        webView.post {
            webView.evaluateJavascript(
                "window.__sr_event&&window.__sr_event('result','$escaped')", null
            )
        }
    }


    override fun onReadyForSpeech(params: Bundle?) {
        dispatchSimple("start")
        dispatchSimple("audiostart")
    }

    override fun onBeginningOfSpeech() {
        dispatchSimple("speechstart")
    }

    override fun onEndOfSpeech() {
        dispatchSimple("speechend")
        dispatchSimple("audioend")
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        if (matches.isNullOrEmpty()) {
            dispatchError("no-speech")
            dispatchSimple("end")
            return
        }
        dispatchResults(matches, confidences, isFinal = true)
        dispatchSimple("end")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches =
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return
        dispatchResults(matches, null, isFinal = false)
    }

    override fun onError(error: Int) {
        val code = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
            SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
            else -> "aborted"
        }
        dispatchError(code)
        dispatchSimple("end")
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val BRIDGE_OBJECT_NAME = SpeechPolyfill.BRIDGE_OBJECT_NAME
        val POLYFILL_JS: String get() = SpeechPolyfill.JS
    }
}
