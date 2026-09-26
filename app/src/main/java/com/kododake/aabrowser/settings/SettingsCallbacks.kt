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

package com.kododake.aabrowser.settings

data class SettingsCallbacks(
    val onClose: () -> Unit = {},
    val onDismiss: () -> Unit = onClose,
    val onThemeChanged: () -> Unit = {},
    val onScaleChanged: () -> Unit = {},
    val onHomePageChanged: () -> Unit = {},
    val onInAppControlsChanged: () -> Unit = {},
    val onPickStartPageBackground: (() -> Unit)? = null,
    val onClearStartPageBackground: (() -> Unit)? = null,
    val onSponsorsVisibilityChanged: () -> Unit = {},
    val onDrmL3EnforcerChanged: () -> Unit = {},
    val onUserAgentChanged: () -> Unit = {}
)

