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
