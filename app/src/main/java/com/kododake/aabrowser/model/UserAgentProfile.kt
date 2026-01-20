package com.kododake.aabrowser.model

import com.kododake.aabrowser.R

enum class UserAgentProfile(
    val storageKey: String,
    val titleRes: Int,
    val subtitleRes: Int
) {
    ANDROID_CHROME(
        storageKey = "android_chrome",
        titleRes = R.string.settings_user_agent_android,
        subtitleRes = R.string.settings_user_agent_android_subtitle
    ),
    SAFARI(
        storageKey = "safari",
        titleRes = R.string.settings_user_agent_safari,
        subtitleRes = R.string.settings_user_agent_safari_subtitle
    );

    companion object {
        fun fromKey(key: String?): UserAgentProfile {
            return values().firstOrNull { it.storageKey == key } ?: ANDROID_CHROME
        }
    }
}
