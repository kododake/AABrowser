package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Persistent remote filter-list subscriptions with conditional, atomic updates. */
object RemoteFilterListManager {
    data class Subscription(
        val id: String,
        val title: String,
        val url: String,
        val enabled: Boolean,
        val builtIn: Boolean,
        val lastUpdated: Long = 0,
        val ruleCount: Int = 0,
        val etag: String? = null,
        val lastModified: String? = null,
        val lastError: String? = null
    )

    data class UpdateResult(val updated: Int, val unchanged: Int, val failed: Int)

    private const val PREFS = "remote_filter_lists"
    private const val KEY_SUBSCRIPTIONS = "subscriptions"
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_LIST_BYTES = 16L * 1024 * 1024
    private val TRUSTED_LIST_IDS = setOf(
        "ublock-filters", "ublock-privacy", "ublock-unbreak", "ublock-quick-fixes"
    )
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "adblock-list-updater")
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val defaults = listOf(
        Subscription("ublock-filters", "uBlock filters – Ads", "https://ublockorigin.github.io/uAssets/filters/filters.txt", true, true),
        Subscription("ublock-privacy", "uBlock filters – Privacy", "https://ublockorigin.github.io/uAssets/filters/privacy.txt", true, true),
        Subscription("ublock-unbreak", "uBlock filters – Unbreak", "https://ublockorigin.github.io/uAssets/filters/unbreak.txt", true, true),
        Subscription("ublock-quick-fixes", "uBlock filters – Quick fixes", "https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt", true, true),
        Subscription("easylist", "EasyList", "https://ublockorigin.github.io/uAssets/thirdparties/easylist.txt", true, true),
        Subscription("easyprivacy", "EasyPrivacy", "https://ublockorigin.github.io/uAssets/thirdparties/easyprivacy.txt", true, true)
    )

    @Synchronized
    fun subscriptions(context: Context): List<Subscription> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SUBSCRIPTIONS, null)
            ?: return defaults
        val decoded = runCatching { decode(stored) }.getOrDefault(emptyList())
        if (decoded.isEmpty()) return defaults
        // Add defaults introduced by a later app version without changing existing choices.
        val ids = decoded.mapTo(HashSet(), Subscription::id)
        return decoded + defaults.filterNot { it.id in ids }
    }

    @Synchronized
    fun setEnabled(context: Context, enabledIds: Set<String>) {
        save(context, subscriptions(context).map { it.copy(enabled = it.id in enabledIds) })
    }

    fun setEnabledAsync(context: Context, enabledIds: Set<String>, callback: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            setEnabled(appContext, enabledIds)
            AdBlocker.reload(appContext)
            scheduleAutoUpdate(appContext)
            callback?.let { cb -> mainHandler.post(cb) }
        }
    }

    @Synchronized
    fun addCustom(context: Context, title: String, url: String): Subscription {
        require(isValidRemoteUrl(url)) { "Only public HTTPS list URLs are supported" }
        val normalizedUrl = URI(url.trim()).normalize().toString()
        require(subscriptions(context).none { it.url == normalizedUrl }) { "This list is already subscribed" }
        val subscription = Subscription(
            id = "custom-${UUID.randomUUID()}",
            title = title.trim().ifEmpty { URI(normalizedUrl).host },
            url = normalizedUrl,
            enabled = true,
            builtIn = false
        )
        save(context, subscriptions(context) + subscription)
        return subscription
    }

    @Synchronized
    private fun removeCustom(context: Context, id: String): Boolean {
        val current = subscriptions(context)
        val target = current.firstOrNull { it.id == id && !it.builtIn } ?: return false
        save(context, current - target)
        cacheFile(context, target).delete()
        return true
    }

    fun removeCustomAsync(context: Context, id: String, callback: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            val removed = removeCustom(appContext, id)
            if (removed) AdBlocker.reload(appContext)
            callback?.let { cb -> mainHandler.post { cb(removed) } }
        }
    }

    fun cachedFilterLines(context: Context): Sequence<FilterEngine.SourceLine> = sequence {
        subscriptions(context).filter(Subscription::enabled).forEach { subscription ->
            val file = cacheFile(context, subscription)
            if (file.isFile) file.bufferedReader().use { reader ->
                while (true) yield(FilterEngine.SourceLine(
                    reader.readLine() ?: break,
                    trusted = subscription.builtIn && subscription.id in TRUSTED_LIST_IDS
                ))
            }
        }
    }

    /** Cheap cache key; list files are atomically replaced whenever their content changes. */
    internal fun cacheFingerprint(context: Context): String = buildString {
        subscriptions(context).forEach { subscription ->
            val file = cacheFile(context, subscription)
            append(subscription.id).append(':')
                .append(subscription.enabled).append(':')
                .append(subscription.lastUpdated).append(':')
                .append(subscription.etag.orEmpty()).append(':')
                .append(subscription.lastModified.orEmpty()).append(':')
                .append(file.length()).append(':')
                .append(file.lastModified()).append(';')
        }
    }

    fun scheduleAutoUpdate(context: Context) {
        val now = System.currentTimeMillis()
        if (subscriptions(context).none { it.enabled && now - it.lastUpdated >= UPDATE_INTERVAL_MS }) return
        refresh(context, force = false)
    }

    fun refresh(context: Context, force: Boolean = true, callback: ((UpdateResult) -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            val now = System.currentTimeMillis()
            var updated = 0
            var unchanged = 0
            var failed = 0
            val next = subscriptions(appContext).map { subscription ->
                if (!subscription.enabled || (!force && now - subscription.lastUpdated < UPDATE_INTERVAL_MS)) {
                    subscription
                } else {
                    when (val result = download(appContext, subscription)) {
                        is DownloadResult.Updated -> {
                            updated++
                            subscription.copy(
                                lastUpdated = now, ruleCount = result.ruleCount,
                                etag = result.etag, lastModified = result.lastModified, lastError = null
                            )
                        }
                        DownloadResult.Unchanged -> {
                            unchanged++
                            subscription.copy(lastUpdated = now, lastError = null)
                        }
                        is DownloadResult.Failed -> {
                            failed++
                            subscription.copy(lastError = result.message)
                        }
                    }
                }
            }
            save(appContext, next)
            if (updated > 0) AdBlocker.reload(appContext)
            callback?.let { cb -> mainHandler.post { cb(UpdateResult(updated, unchanged, failed)) } }
        }
    }

    internal fun isValidRemoteUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.host.isNullOrBlank()) return false
        val host = uri.host.lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host == "0.0.0.0" || host == "::1" || host == "[::1]") return false
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size == 4 && (octets[0] == 10 || octets[0] == 127 ||
                    (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 172 && octets[1] in 16..31))) return false
        return true
    }

    private fun download(context: Context, subscription: Subscription): DownloadResult {
        val request = Request.Builder().url(subscription.url).apply {
            subscription.etag?.let { header("If-None-Match", it) }
            subscription.lastModified?.let { header("If-Modified-Since", it) }
            header("User-Agent", "AA-Browser filter updater")
        }.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 304) return DownloadResult.Unchanged
                if (!response.isSuccessful) return DownloadResult.Failed("HTTP ${response.code}")
                if (!isValidRemoteUrl(response.request.url.toString())) return DownloadResult.Failed("Redirected to an unsafe URL")
                val body = response.body
                if ((body.contentLength() > MAX_LIST_BYTES)) return DownloadResult.Failed("List is larger than 16 MB")
                val target = cacheFile(context, subscription)
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, "${target.name}.download")
                var total = 0L
                var lines = 0
                try {
                    body.byteStream().buffered().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_LIST_BYTES) return DownloadResult.Failed("List is larger than 16 MB")
                                for (index in 0 until count) if (buffer[index] == '\n'.code.toByte()) lines++
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (total == 0L) return DownloadResult.Failed("List is empty")
                    runCatching {
                        java.nio.file.Files.move(
                            temporary.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE
                        )
                    }.getOrElse {
                        java.nio.file.Files.move(
                            temporary.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
                DownloadResult.Updated(lines + 1, response.header("ETag"), response.header("Last-Modified"))
            }
        }.getOrElse { DownloadResult.Failed(it.message ?: "Download failed") }
    }

    private fun cacheFile(context: Context, subscription: Subscription): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(subscription.id.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "filter-lists/$digest.txt")
    }

    @Synchronized
    private fun save(context: Context, subscriptions: List<Subscription>) {
        val array = JSONArray()
        subscriptions.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("url", item.url)
            put("enabled", item.enabled); put("builtIn", item.builtIn)
            put("lastUpdated", item.lastUpdated); put("ruleCount", item.ruleCount)
            put("etag", item.etag); put("lastModified", item.lastModified); put("lastError", item.lastError)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply()
    }

    private fun decode(json: String): List<Subscription> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(Subscription(
                    id = item.getString("id"), title = item.getString("title"), url = item.getString("url"),
                    enabled = item.optBoolean("enabled", true), builtIn = item.optBoolean("builtIn", false),
                    lastUpdated = item.optLong("lastUpdated"), ruleCount = item.optInt("ruleCount"),
                    etag = item.optString("etag").takeIf(String::isNotBlank),
                    lastModified = item.optString("lastModified").takeIf(String::isNotBlank),
                    lastError = item.optString("lastError").takeIf(String::isNotBlank)
                ))
            }
        }
    }

    private sealed interface DownloadResult {
        data class Updated(val ruleCount: Int, val etag: String?, val lastModified: String?) : DownloadResult
        data class Failed(val message: String) : DownloadResult
        data object Unchanged : DownloadResult
    }
}
