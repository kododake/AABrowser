package com.kododake.aabrowser.model

enum class QuickActionButtonPosition(
    val storageKey: String
) {
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right"),
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right");

    companion object {
        fun fromKey(key: String?): QuickActionButtonPosition {
            return entries.firstOrNull { it.storageKey == key } ?: BOTTOM_LEFT
        }
    }
}
