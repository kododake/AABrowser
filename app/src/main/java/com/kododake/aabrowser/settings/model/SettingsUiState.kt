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

package com.kododake.aabrowser.settings.model

import com.kododake.aabrowser.model.AppThemeMode
import com.kododake.aabrowser.model.QuickActionButtonMode
import com.kododake.aabrowser.model.QuickActionButtonPosition
import com.kododake.aabrowser.model.UserAgentProfile

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
    val isBetaForceDarkEnabled: Boolean = false,
    val globalScalePercent: Int = 100,
    val homePageUrl: String? = null,
    val isRestoreTabsEnabled: Boolean = true,
    val isResumeLastPageEnabled: Boolean = true,
    val isAlwaysShowUrlBarEnabled: Boolean = false,
    val quickActionButtonMode: QuickActionButtonMode = QuickActionButtonMode.MENU,
    val isQuickActionAlwaysVisible: Boolean = false,
    val quickActionButtonPosition: QuickActionButtonPosition = QuickActionButtonPosition.BOTTOM_LEFT,
    val startPageSiteCount: Int = 0,
    val startPageBackgroundUri: String? = null,
    val userAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_CHROME,
)

sealed interface SettingsEvent {
    data class SetThemeMode(val mode: AppThemeMode) : SettingsEvent
    data class SetBetaForceDark(val enabled: Boolean) : SettingsEvent
    data class SetGlobalScalePercent(val percent: Int) : SettingsEvent
    data class SetHomePageUrl(val url: String?) : SettingsEvent
    data class SetRestoreTabs(val enabled: Boolean) : SettingsEvent
    data class SetResumeLastPage(val enabled: Boolean) : SettingsEvent
    data class SetAlwaysShowUrlBar(val enabled: Boolean) : SettingsEvent
    data class SetQuickActionButtonMode(val mode: QuickActionButtonMode) : SettingsEvent
    data class SetQuickActionAlwaysVisible(val enabled: Boolean) : SettingsEvent
    data class SetQuickActionButtonPosition(val position: QuickActionButtonPosition) : SettingsEvent
    object PickStartPageBackground : SettingsEvent
    object ClearStartPageBackground : SettingsEvent
    data class SetUserAgentProfile(val profile: UserAgentProfile) : SettingsEvent
    object ClearSitePermissions : SettingsEvent
    object ClearHttpHosts : SettingsEvent
    object ClearCookies : SettingsEvent
    object CloseSettings : SettingsEvent
}
