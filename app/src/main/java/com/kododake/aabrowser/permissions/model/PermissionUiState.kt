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

package com.kododake.aabrowser.permissions.model

import android.net.Uri

sealed interface PermissionDialogType {
    object Cleartext : PermissionDialogType
    object Microphone : PermissionDialogType
    object Location : PermissionDialogType
    object LocationUpgrade : PermissionDialogType
}

data class PermissionDialogState(
    val type: PermissionDialogType,
    val origin: Uri?,
    val isSecure: Boolean = true
)

sealed interface PermissionEvent {
    object AllowOnce : PermissionEvent
    object AllowHostPermanently : PermissionEvent
    object DenyOrCancel : PermissionEvent
    object KeepApproximateLocation : PermissionEvent
    object UpgradeToPreciseLocation : PermissionEvent
}
