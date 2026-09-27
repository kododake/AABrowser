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

package com.kododake.aabrowser.main

import android.content.Context
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors

object WebViewWarmupHelper {

    fun warmup(context: Context) {
        val backgroundExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "WebViewWarmupThread").apply { isDaemon = true }
        }
        val startupConfig = WebViewStartUpConfig.Builder(backgroundExecutor).build()
        backgroundExecutor.execute {
            runCatching {
                WebViewCompat.startUpWebView(
                    context.applicationContext,
                    startupConfig,
                    object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                        override fun onResult(result: WebViewStartUpResult) {
                            Log.i("WebKitWarmup", "WebView warmed up successfully. Async startup complete.")
                            backgroundExecutor.shutdown()
                        }
                        override fun onError(error: WebViewStartupException) {
                            Log.w("WebKitWarmup", "WebView warmup failed: ${error.message}")
                            backgroundExecutor.shutdown()
                        }
                    }
                )
            }.onFailure { e ->
                Log.w("WebKitWarmup", "startUpWebView invocation failed: ${e.message}")
                backgroundExecutor.shutdown()
            }
        }
    }
}
