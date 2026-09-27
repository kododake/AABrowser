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

/** Shields (ad and tracker blocking) preferences: the global switch and the per-site allowlist. */
object ShieldsPreferences {
    const val PREFS_NAME = "browser_prefs"
    private const val KEY_SHIELDS_ENABLED = "shields_enabled"
    private const val KEY_SHIELDS_DISABLED_HOSTS = "shields_disabled_hosts"

    fun isShieldsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHIELDS_ENABLED, true)
    }

    fun setShieldsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHIELDS_ENABLED, enabled)
            .apply()
    }

    /** Hosts (and their subdomains) on which the user switched Shields off. */
    fun getShieldsDisabledHosts(context: Context): Set<String> = loadHostList(context)

    fun isShieldsDisabledForHost(context: Context, host: String?): Boolean {
        val normalizedHost = host?.trim()?.lowercase()
        if (normalizedHost.isNullOrEmpty()) return false
        return normalizedHost in loadHostList(context)
    }

    fun setShieldsDisabledForHost(context: Context, host: String, disabled: Boolean) {
        val normalizedHost = host.trim().lowercase()
        if (normalizedHost.isEmpty()) return
        val current = loadHostList(context).toMutableSet()
        val changed = if (disabled) current.add(normalizedHost) else current.remove(normalizedHost)
        if (!changed) return
        val out = JSONArray()
        current.forEach { out.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHIELDS_DISABLED_HOSTS, out.toString())
            .apply()
    }

    private fun loadHostList(context: Context): Set<String> {
        val serialized = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SHIELDS_DISABLED_HOSTS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(serialized)
            buildSet(array.length()) {
                for (i in 0 until array.length()) {
                    array.optString(i).trim().lowercase().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }
}
