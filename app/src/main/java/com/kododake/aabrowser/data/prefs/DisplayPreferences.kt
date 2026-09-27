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
import android.content.res.Configuration
import com.kododake.aabrowser.model.AppThemeMode
import kotlin.math.roundToInt

object DisplayPreferences {
    const val PREFS_NAME = "browser_prefs"
    private const val KEY_GLOBAL_SCALE_PERCENT = "global_scale_percent"
    private const val KEY_THEME_MODE = "theme_mode"

    const val MIN_GLOBAL_SCALE_PERCENT = 40
    const val MAX_GLOBAL_SCALE_PERCENT = 200
    const val DEFAULT_GLOBAL_SCALE_PERCENT = 100

    fun getGlobalScalePercent(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sanitizeGlobalScalePercent(prefs.getInt(KEY_GLOBAL_SCALE_PERCENT, DEFAULT_GLOBAL_SCALE_PERCENT))
    }

    fun sanitizeGlobalScalePercent(percent: Int): Int {
        return percent.coerceIn(MIN_GLOBAL_SCALE_PERCENT, MAX_GLOBAL_SCALE_PERCENT)
    }

    fun setGlobalScalePercent(context: Context, percent: Int) {
        val sanitized = sanitizeGlobalScalePercent(percent)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GLOBAL_SCALE_PERCENT, sanitized)
            .apply()
    }

    fun createScaledContext(base: Context): Context {
        val scalePercent = getGlobalScalePercent(base)
        if (scalePercent == DEFAULT_GLOBAL_SCALE_PERCENT) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        val defaultDensityDpi = base.resources.displayMetrics.densityDpi
        val scaledDensityDpi = ((defaultDensityDpi * scalePercent) / 100f).roundToInt()
        config.densityDpi = scaledDensityDpi.coerceAtLeast(120)
        return base.createConfigurationContext(config)
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.AUTO.storageKey)
        return AppThemeMode.fromKey(raw)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageKey)
            .apply()
    }
}
