package com.kododake.aabrowser.model

enum class QuickActionButtonMode(
    val storageKey: String
) {
    MENU("menu"),
    ADDRESS_BAR("address_bar");

    companion object {
        fun fromKey(key: String?): QuickActionButtonMode {
            return entries.firstOrNull { it.storageKey == key } ?: MENU
        }
    }
}
