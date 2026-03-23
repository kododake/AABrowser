package com.kododake.aabrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

object SiteIconCache {
    private const val ICON_DIR = "site-icons"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlightHosts = Collections.synchronizedSet(mutableSetOf<String>())

    fun getCachedIcon(context: Context, url: String?): Bitmap? {
        val file = iconFile(context, url) ?: return null
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun cacheIcon(context: Context, pageUrl: String?, bitmap: Bitmap?) {
        if (bitmap == null) return
        val file = iconFile(context, pageUrl) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
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
        if (!inFlightHosts.add(hostKey)) return

        Thread {
            val bitmap = runCatching {
                val request = Request.Builder()
                    .url(resolveIconUrl(pageUrl) ?: return@runCatching null)
                    .header("User-Agent", "AABrowser Icon Fetcher")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            }.getOrNull()

            if (bitmap != null) {
                cacheIcon(context, pageUrl, bitmap)
            }

            inFlightHosts.remove(hostKey)
            mainHandler.post { onComplete(bitmap) }
        }.start()
    }

    private fun iconFile(context: Context, url: String?): File? {
        val hostKey = hostKey(url) ?: return null
        return File(File(context.cacheDir, ICON_DIR), "$hostKey.png")
    }

    private fun hostKey(url: String?): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
            ?.replace(Regex("[^a-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
        return host
    }

    private fun resolveIconUrl(pageUrl: String?): String? {
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return when (host) {
            "m.youtube.com",
            "www.youtube.com",
            "youtube.com" -> "https://www.youtube.com/favicon.ico"
            "www.google.com",
            "google.com" -> "https://www.google.com/favicon.ico"
            "www.twitch.tv",
            "twitch.tv" -> "https://www.twitch.tv/favicon.ico"
            "www.kick.com",
            "kick.com" -> "https://kick.com/favicon.ico"
            "www.wikipedia.org",
            "wikipedia.org" -> "https://www.wikipedia.org/static/favicon/wikipedia.ico"
            "www.weather.com",
            "weather.com" -> "https://weather.com/favicon.ico"
            else -> {
                val scheme = uri.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) } ?: "https"
                "$scheme://$host/favicon.ico"
            }
        }
    }
}
