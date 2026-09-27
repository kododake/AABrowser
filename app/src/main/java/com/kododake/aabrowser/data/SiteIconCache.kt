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

package com.kododake.aabrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit


object SiteIconCache {
    private const val ICON_DIR = "site-icons-v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlightCallbacks = Collections.synchronizedMap(mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>())
    private val diskExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 16
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    fun getCachedIcon(context: Context, url: String?): Bitmap? {
        val hostKey = hostKey(url) ?: return null
        val memBitmap = memoryCache.get(hostKey)
        if (memBitmap != null) return memBitmap

        val file = iconFile(context, url) ?: return null
        if (!file.exists()) return null
        val diskBitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (diskBitmap != null) {
            memoryCache.put(hostKey, diskBitmap)
        }
        return diskBitmap
    }

    fun cacheIcon(context: Context, pageUrl: String?, bitmap: Bitmap?, overwriteExisting: Boolean = false) {
        if (bitmap == null) return
        val hostKey = hostKey(pageUrl) ?: return
        if (!overwriteExisting && memoryCache.get(hostKey) != null) return
        val file = iconFile(context, pageUrl) ?: return
        if (!overwriteExisting && file.exists()) return
        memoryCache.put(hostKey, bitmap)
        diskExecutor.execute {
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
        }
    }

    fun prefetchIconIfNeeded(
        context: Context,
        pageUrl: String?,
        onComplete: (Bitmap?) -> Unit = {}
    ) {
        val cached = getCachedIcon(context, pageUrl)
        if (cached != null) {
            onComplete(cached)
            return
        }

        val hostKey = hostKey(pageUrl) ?: run {
            onComplete(null)
            return
        }

        var isFirstRequest = false
        val listToNotify = synchronized(inFlightCallbacks) {
            val list = inFlightCallbacks[hostKey]
            if (list == null) {
                isFirstRequest = true
                val newList = mutableListOf(onComplete)
                inFlightCallbacks[hostKey] = newList
                newList
            } else {
                list.add(onComplete)
                null
            }
        }

        if (!isFirstRequest) return

        Thread {
            val host = extractHost(pageUrl)
            val bitmap = host?.let { fetchIconForHost(it) }

            if (bitmap != null) {
                cacheIcon(context, pageUrl, bitmap, overwriteExisting = true)
            }

            val pending = synchronized(inFlightCallbacks) {
                inFlightCallbacks.remove(hostKey)
            }

            if (pending != null) {
                mainHandler.post {
                    pending.forEach { callback ->
                        callback(bitmap)
                    }
                }
            }
        }.start()
    }

    private fun fetchIconForHost(host: String): Bitmap? {
        val urlsToTry = listOf(
            "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$host&size=128",
            "https://www.google.com/s2/favicons?domain=$host&sz=128",
            "https://icons.duckduckgo.com/ip3/$host.ico",
            "https://$host/favicon.ico"
        )
        for (iconUrl in urlsToTry) {
            val bmp = runCatching {
                val request = Request.Builder()
                    .url(iconUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            }.getOrNull()
            if (bmp != null && bmp.width > 1 && bmp.height > 1) {
                return bmp
            }
        }
        return null
    }

    private fun iconFile(context: Context, url: String?): File? {
        val hostKey = hostKey(url) ?: return null
        return File(File(context.cacheDir, ICON_DIR), "$hostKey.png")
    }

    fun hostKey(url: String?): String? {
        val host = extractHost(url) ?: return null
        return host.replace(Regex("[^a-z0-9._-]"), "_").takeIf { it.isNotBlank() }
    }

    fun extractHost(url: String?): String? {
        val raw = url?.trim() ?: return null
        if (raw.isBlank()) return null
        val normalized = if (raw.contains("://")) raw else "https://$raw"
        val host = runCatching { Uri.parse(normalized).host?.lowercase() }.getOrNull() ?: return null
        val cleaned = host.removePrefix("www.")
        return cleaned.takeIf { it.isNotBlank() }
    }
}
