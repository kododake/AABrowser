package com.kododake.aabrowser.web.adblock

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.webkit.WebResourceResponse
import com.kododake.aabrowser.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Android/WebView adapter around the uBlock-style [FilterEngine]. */
object AdBlocker {
    private const val FILTERS_ASSET = "adblock/blocklist.txt"
    private const val CACHE_MAGIC = 0x41414246
    private const val CACHE_VERSION = 3
    private const val MAX_BRIDGE_TOKENS = 512
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024

    @Volatile
    private var engine = FilterEngine.parse(emptySequence())

    @Volatile
    private var loaded = false

    val isLoaded: Boolean
        get() = loaded

    private var loading = false
    private val readyCallbacks = ArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "adblock-filter-loader")
    }

    private val sessionBlockCount = AtomicLong(0)

    val blockedThisSession: Long
        get() = sessionBlockCount.get()

    fun resetSessionCount() {
        sessionBlockCount.set(0)
    }

    /** Starts parsing the static filter lists off the main thread. */
    fun ensureLoadedAsync(context: Context) {
        runWhenLoaded(context) {}
    }

    /**
     * Runs [action] on the main thread once all filters are compiled.
     * Navigation uses this to ensure the first request cannot bypass Shields.
     */
    fun runWhenLoaded(context: Context, action: () -> Unit) {
        if (loaded) {
            runOnMainThread(action)
            return
        }

        val appContext = context.applicationContext
        var startLoader = false
        var runImmediately = false
        synchronized(this) {
            if (loaded) {
                runImmediately = true
            } else {
                readyCallbacks += action
                if (!loading) {
                    loading = true
                    startLoader = true
                }
            }
        }
        if (runImmediately) {
            runOnMainThread(action)
            return
        }
        if (!startLoader) return

        loader.execute {
            val replacement = loadEngine(appContext)
            val callbacks: List<() -> Unit>
            synchronized(this) {
                engine = replacement
                loaded = true
                loading = false
                callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
            }
            mainHandler.post { callbacks.forEach { it() } }
            RemoteFilterListManager.scheduleAutoUpdate(appContext)
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    /** Rebuilds the immutable engine after subscription or cache changes. */
    fun reload(context: Context) {
        val replacement = loadEngine(context.applicationContext)
        synchronized(this) {
            engine = replacement
            loaded = true
        }
    }

    private fun loadEngine(context: Context): FilterEngine {
        val fingerprint = "${BuildConfig.VERSION_CODE}:${RemoteFilterListManager.cacheFingerprint(context)}"
        readCachedEngine(context, fingerprint)?.let { return it }
        val replacement = runCatching {
            FilterEngine.parseSources(sequence {
                context.assets.open(FILTERS_ASSET).bufferedReader().use { reader ->
                    while (true) yield(FilterEngine.SourceLine(reader.readLine() ?: break, trusted = true))
                }
                yieldAll(RemoteFilterListManager.cachedFilterLines(context))
            })
        }.getOrElse {
            FilterEngine.parseSources(context.assets.open(FILTERS_ASSET).bufferedReader().use { reader ->
                reader.readLines().asSequence().map { FilterEngine.SourceLine(it, trusted = true) }
            })
        }
        writeCachedEngine(context, fingerprint, replacement)
        return replacement
    }

    private fun readCachedEngine(context: Context, fingerprint: String): FilterEngine? = runCatching {
        val file = engineCacheFile(context)
        deleteStaleCaches(file)
        if (!file.isFile || file.length() !in 1..MAX_CACHE_BYTES) return@runCatching null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION ||
                input.readUTF() != fingerprint) return@use null
            FilterEngine.readSnapshot(input)
        }
    }.getOrNull()

    private fun writeCachedEngine(context: Context, fingerprint: String, engine: FilterEngine) {
        runCatching {
            val target = engineCacheFile(context)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            try {
                DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
                    output.writeInt(CACHE_MAGIC)
                    output.writeInt(CACHE_VERSION)
                    output.writeUTF(fingerprint)
                    engine.writeSnapshot(output)
                }
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
        }
    }

    private fun engineCacheFile(context: Context) =
        File(context.filesDir, "adblock/compiled-engine-$CACHE_VERSION.bin")

    /** Snapshots written by earlier app versions use an older format; free the space. */
    private fun deleteStaleCaches(current: File) {
        current.parentFile?.listFiles()?.forEach { file ->
            if (file != current && file.name.startsWith("compiled-engine-") && file.name.endsWith(".bin")) file.delete()
        }
    }

    fun scriptletInvocations(pageUrl: String?): List<FilterEngine.ScriptletInvocation> {
        val current = engine
        if (current.pagePolicy(pageUrl).document) return emptyList()
        return current.scriptletsFor(pageUrl)
    }

    /** Content exceptions that apply to the page at [pageUrl]. */
    fun pagePolicy(pageUrl: String?): FilterEngine.PagePolicy = engine.pagePolicy(pageUrl)

    fun interceptOrNull(
        requestUrl: Uri?,
        pageUrl: String?,
        pageHost: String? = null,
        isMainFrame: Boolean,
        requestHeaders: Map<String, String> = emptyMap()
    ): WebResourceResponse? {
        if (isMainFrame || requestUrl == null) return null
        val scheme = requestUrl.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val current = engine
        if (pageUrl != null && current.pagePolicy(pageUrl).document) return null
        if (!current.shouldBlock(FilterEngine.Request(
                url = requestUrl.toString(),
                pageUrl = pageUrl,
                resourceType = inferResourceType(requestUrl, requestHeaders),
                requestHost = requestUrl.host,
                pageHost = pageHost
            ))) return null

        sessionBlockCount.incrementAndGet()
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /**
     * JSON for the document-start cosmetic script: `{"s":[...],"o":[...],"g":true}` with the site's
     * specific hide selectors, the un-keyed generic selectors, and whether keyed generic rules
     * should be requested; an empty string when nothing applies to [pageUrl].
     */
    fun cosmeticInitJson(pageUrl: String?): String {
        val current = engine
        val policy = current.pagePolicy(pageUrl)
        if (policy.hidesNothing) return ""
        val init = current.cosmeticInit(pageUrl, allowGeneric = policy.allowGeneric, allowSpecific = policy.allowSpecific) ?: return ""
        return org.json.JSONObject()
            .put("s", org.json.JSONArray(init.specific))
            .put("o", org.json.JSONArray(init.other))
            .put("g", init.generic)
            .toString()
    }

    /**
     * JSON array of generic hide selectors keyed by the given page classes/ids (JSON arrays of
     * strings, capped so a hostile page cannot make the bridge do unbounded work).
     */
    fun genericSelectorsJson(pageUrl: String?, classesJson: String?, idsJson: String?): String {
        val host = FilterEngine.hostOf(pageUrl) ?: return ""
        val classes = parseTokenArray(classesJson)
        val ids = parseTokenArray(idsJson)
        if (classes.isEmpty() && ids.isEmpty()) return ""
        val current = engine
        if (!current.pagePolicy(pageUrl).allowGeneric) return ""
        val selectors = current.genericSelectorsFor(classes, ids, host)
        return if (selectors.isEmpty()) "" else org.json.JSONArray(selectors).toString()
    }

    private fun parseTokenArray(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>(minOf(array.length(), MAX_BRIDGE_TOKENS))
        for (index in 0 until minOf(array.length(), MAX_BRIDGE_TOKENS)) {
            val token = array.optString(index, "")
            if (token.isNotEmpty() && token.length <= 128) out += token
        }
        return out
    }

    private fun inferResourceType(uri: Uri, headers: Map<String, String>): FilterEngine.ResourceType {
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value.orEmpty()
        val path = uri.path.orEmpty()
        return when {
            accept.contains("text/css", ignoreCase = true) || path.endsWith(".css", ignoreCase = true) -> FilterEngine.ResourceType.STYLESHEET
            accept.contains("image/", ignoreCase = true) || path.endsWithAny(IMAGE_EXTENSIONS) -> FilterEngine.ResourceType.IMAGE
            accept.contains("font/", ignoreCase = true) || path.endsWithAny(FONT_EXTENSIONS) -> FilterEngine.ResourceType.FONT
            accept.contains("audio/", ignoreCase = true) || accept.contains("video/", ignoreCase = true) || path.endsWithAny(MEDIA_EXTENSIONS) -> FilterEngine.ResourceType.MEDIA
            accept.contains("javascript", ignoreCase = true) || path.endsWithAny(SCRIPT_EXTENSIONS) -> FilterEngine.ResourceType.SCRIPT
            accept.contains("application/json", ignoreCase = true) || accept.contains("text/event-stream", ignoreCase = true) -> FilterEngine.ResourceType.XHR
            accept.contains("text/html", ignoreCase = true) -> FilterEngine.ResourceType.SUBDOCUMENT
            else -> FilterEngine.ResourceType.OTHER
        }
    }

    private fun String.endsWithAny(suffixes: Array<String>): Boolean =
        suffixes.any { endsWith(it, ignoreCase = true) }

    private val IMAGE_EXTENSIONS = arrayOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".avif", ".ico")
    private val FONT_EXTENSIONS = arrayOf(".woff", ".woff2", ".ttf", ".otf", ".eot")
    private val MEDIA_EXTENSIONS = arrayOf(".mp3", ".mp4", ".webm", ".m3u8", ".ts", ".ogg", ".wav")
    private val SCRIPT_EXTENSIONS = arrayOf(".js", ".mjs")
}
