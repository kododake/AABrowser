package com.kododake.aabrowser.web

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the ad blocklist fresh by periodically downloading AdGuard's free,
 * publicly hosted "DNS filter" (a domain-level list maintained by AdGuard
 * Software Limited). This is the legal, GPL-compatible way to get
 * AdGuard-quality blocking without bundling their proprietary engine — and it
 * means the list maintains itself instead of us editing domains by hand.
 *
 * Flow:
 *  - On startup [AdBlocker] calls [loadCached] to load the last downloaded copy
 *    instantly, then [maybeUpdate] to refresh it if it is missing or stale.
 *  - Downloads run on a background thread, are rate-limited to once per
 *    [UPDATE_INTERVAL_MS], and write atomically via a temp file so a failed or
 *    partial download never corrupts the cache.
 *  - Every failure is non-fatal: we always still have the bundled asset list and
 *    the hard-coded core list to fall back on.
 */
object FilterListUpdater {

    private const val TAG = "FilterListUpdater"

    /**
     * AdGuard DNS filter (domain list). Plain "||domain^" rules that map cleanly
     * to whole-domain blocks. GitHub raw is used for a stable, long-lived URL.
     */
    private const val FILTER_URL =
        "https://raw.githubusercontent.com/AdguardTeam/AdGuardSDNSFilter/master/Filters/filter.txt"

    private const val CACHE_FILE_NAME = "adblock_remote.txt"
    private const val TMP_FILE_NAME = "adblock_remote.tmp"
    private const val PREFS_NAME = "adblock_prefs"
    private const val KEY_LAST_UPDATE = "last_update_ms"

    /** Refresh at most once a week. */
    private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

    /** Safety cap so a hijacked/oversized response can't exhaust storage. */
    private const val MAX_BYTES = 12L * 1024 * 1024 // 12 MB

    private val updating = AtomicBoolean(false)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE_NAME)

    /** Load the previously downloaded list (if any) into the blocker. */
    fun loadCached(context: Context) {
        val file = cacheFile(context)
        if (!file.exists() || file.length() == 0L) return
        runCatching {
            file.bufferedReader().use { AdBlocker.ingest(it) }
        }.onFailure { Log.w(TAG, "Failed to load cached filter list: ${it.message}") }
    }

    /**
     * Download a fresh copy if the cache is missing or older than
     * [UPDATE_INTERVAL_MS]. No-op if a download is already in progress.
     */
    fun maybeUpdate(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val cacheReady = cacheFile(appContext).let { it.exists() && it.length() > 0L }
        val fresh = (System.currentTimeMillis() - lastUpdate) < UPDATE_INTERVAL_MS
        if (cacheReady && fresh) return

        if (!updating.compareAndSet(false, true)) return
        Thread {
            try {
                download(appContext, prefs)
            } catch (e: Exception) {
                Log.w(TAG, "Filter list update failed: ${e.message}")
            } finally {
                updating.set(false)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun download(
        context: Context,
        prefs: android.content.SharedPreferences
    ) {
        val request = Request.Builder()
            .url(FILTER_URL)
            .header("User-Agent", "AABrowser-AdBlocker")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Filter list HTTP ${response.code}")
                return
            }
            val tmp = File(context.filesDir, TMP_FILE_NAME)

            var written = 0L
            tmp.outputStream().use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_BYTES) {
                            Log.w(TAG, "Filter list exceeded size cap; aborting")
                            tmp.delete()
                            return
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }

            // Validate by parsing the temp file before committing it.
            val added = runCatching {
                tmp.bufferedReader().use { AdBlocker.ingest(it) }
            }.getOrDefault(0)

            if (added <= 0) {
                Log.w(TAG, "Downloaded filter list produced no usable hosts; keeping previous cache")
                tmp.delete()
                return
            }

            // Commit atomically: rename replaces the cache in one step (both
            // files are in filesDir, so it's a same-filesystem move). Fall back
            // to copy if rename is refused by the platform.
            val dest = cacheFile(context)
            val committed = tmp.renameTo(dest) ||
                runCatching { tmp.copyTo(dest, overwrite = true); tmp.delete(); true }
                    .getOrDefault(false)
            if (committed) {
                prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
                Log.i(TAG, "Filter list updated: +$added hosts (total ${AdBlocker.blockedHostCount()})")
            } else {
                tmp.delete()
            }
        }
    }
}
