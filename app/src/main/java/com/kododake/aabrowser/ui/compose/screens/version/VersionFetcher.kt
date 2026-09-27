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

package com.kododake.aabrowser.ui.compose.screens.version

import android.app.Activity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object VersionFetcher {

    fun fetchLatestVersion(
        activity: Activity,
        onSuccess: (latestUrl: String, tagName: String) -> Unit,
        onError: () -> Unit
    ) {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/kododake/AABrowser/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestUrl = json.getString("html_url")
                    val tag = json.getString("tag_name")

                    activity.runOnUiThread {
                        onSuccess(latestUrl, tag)
                    }
                } else {
                    activity.runOnUiThread { onError() }
                }
            } catch (_: Exception) {
                activity.runOnUiThread { onError() }
            }
        }.start()
    }
}
