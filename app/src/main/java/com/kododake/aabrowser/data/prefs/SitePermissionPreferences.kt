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

package com.kododake.aabrowser.data.prefs

import android.content.Context
import org.json.JSONArray

object SitePermissionPreferences {
    const val PREFS_NAME = "browser_prefs"
    private const val KEY_ALLOWED_CLEAR_HOSTS = "allowed_clear_hosts"
    private const val KEY_ALLOWED_LOCATION_HOSTS = "allowed_location_hosts"
    private const val KEY_ALLOWED_MICROPHONE_HOSTS = "allowed_microphone_hosts"

    fun isHostAllowedCleartext(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return getHostSet(context, KEY_ALLOWED_CLEAR_HOSTS).contains(host.lowercase())
    }

    fun addAllowedCleartextHost(context: Context, host: String) {
        addHostToSet(context, KEY_ALLOWED_CLEAR_HOSTS, host)
    }

    fun clearAllowedCleartextHosts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALLOWED_CLEAR_HOSTS)
            .apply()
    }

    fun isHostAllowedLocation(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return getHostSet(context, KEY_ALLOWED_LOCATION_HOSTS).contains(host.lowercase())
    }

    fun addAllowedLocationHost(context: Context, host: String) {
        addHostToSet(context, KEY_ALLOWED_LOCATION_HOSTS, host)
    }

    fun isHostAllowedMicrophone(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return getHostSet(context, KEY_ALLOWED_MICROPHONE_HOSTS).contains(host.lowercase())
    }

    fun addAllowedMicrophoneHost(context: Context, host: String) {
        addHostToSet(context, KEY_ALLOWED_MICROPHONE_HOSTS, host)
    }

    fun clearSavedSitePermissions(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALLOWED_LOCATION_HOSTS)
            .remove(KEY_ALLOWED_MICROPHONE_HOSTS)
            .apply()
    }

    private fun getHostSet(context: Context, key: String): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet(array.length()) {
                for (i in 0 until array.length()) {
                    val host = array.optString(i).trim().lowercase()
                    if (host.isNotEmpty()) add(host)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun addHostToSet(context: Context, key: String, host: String) {
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        val current = getHostSet(context, key).toMutableSet()
        if (current.add(normalized)) {
            val array = JSONArray()
            current.forEach { array.put(it) }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, array.toString())
                .apply()
        }
    }
}
