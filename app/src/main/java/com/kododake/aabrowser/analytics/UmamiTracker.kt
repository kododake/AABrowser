package com.kododake.aabrowser.analytics

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class UmamiTracker(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = false }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val appVersion: String by lazy {
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pInfo.versionName ?: PackageInfoCompat.getLongVersionCode(pInfo).toString()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private val anonymousId: String by lazy {
        val cached = prefs.getString(KEY_USER_ID, null)
        if (!cached.isNullOrBlank()) return@lazy cached
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, created).apply()
        created
    }

    fun trackEvent(eventName: String, eventData: Map<String, String>? = null) {
        val payload = buildJsonObject {
            put("type", "event")
            put("payload", buildJsonObject {
                put("website", WEBSITE_ID)
                put("url", "app://v$appVersion")
                put("hostname", "aabrowser.internal")
                put("name", eventName)
                put("data", buildJsonObject {
                    put("version", appVersion)
                    put("user_id", anonymousId)
                    eventData?.forEach { (key, value) -> put(key, value) }
                })
            })
        }
        sendPayload(payload)
    }

    private fun sendPayload(payload: kotlinx.serialization.json.JsonObject) {
        val requestBody = json.encodeToString(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(UMAMI_ENDPOINT)
            .post(requestBody)
            .addHeader("User-Agent", "$USER_AGENT/$appVersion")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) Log.w(TAG, "Error: ${it.code}")
                }
            }
        })
    }

    private companion object {
        private const val TAG = "UmamiTracker"
        private const val PREFS_NAME = "umami_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val WEBSITE_ID = "0429c890-e9a6-4370-8434-a05aaa54249d"
        private const val UMAMI_ENDPOINT = "https://stats1.kododake.work/api/send"
        private const val USER_AGENT = "Mozilla/5.0 (Android) AABrowser"
    }
}