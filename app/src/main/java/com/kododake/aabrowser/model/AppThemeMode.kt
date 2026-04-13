package com.kododake.aabrowser.model

import androidx.appcompat.app.AppCompatDelegate

enum class AppThemeMode(
    val storageKey: String,
    val nightMode: Int
) {
    AUTO(
        storageKey = "auto",
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ),
    LIGHT(
        storageKey = "light",
        nightMode = AppCompatDelegate.MODE_NIGHT_NO
    ),
    DARK(
        storageKey = "dark",
        nightMode = AppCompatDelegate.MODE_NIGHT_YES
    );

    companion object {
        fun fromKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.storageKey == key } ?: AUTO
        }
    }
}
