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

object WebPreferences {
    const val PREFS_NAME = "browser_prefs"
    private const val KEY_DRM_L3_ENFORCER = "drm_l3_enforcer_enabled"
    const val DEFAULT_DRM_L3_ENFORCER = true
    private const val KEY_CROSS_ORIGIN_ISOLATION = "cross_origin_isolation_enabled"

    fun isDrmL3EnforcerEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DRM_L3_ENFORCER, DEFAULT_DRM_L3_ENFORCER)
    }

    fun setDrmL3EnforcerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DRM_L3_ENFORCER, enabled)
            .apply()
    }

    fun isCrossOriginIsolationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CROSS_ORIGIN_ISOLATION, false)

    fun setCrossOriginIsolationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CROSS_ORIGIN_ISOLATION, enabled)
            .apply()
    }
}
