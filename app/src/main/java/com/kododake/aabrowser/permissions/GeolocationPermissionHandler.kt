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

package com.kododake.aabrowser.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kododake.aabrowser.AppConstants
import com.kododake.aabrowser.data.BrowserPreferences

class GeolocationPermissionHandler(
    private val activity: AppCompatActivity,
    private val dialogs: PermissionDialogs
) {
    var pendingGeolocationOrigin: String? = null
    var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    fun handleGeolocationPermissionRequest(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (callback == null) return
        val uri = runCatching { origin?.let(Uri::parse) }.getOrNull()
        val host = uri?.host?.lowercase()
        val scheme = uri?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"

        if (!isSecure) {
            callback.invoke(origin, false, false)
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (BrowserPreferences.isHostAllowedLocation(activity, host) && hasFine) {
            callback.invoke(origin, true, true)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || dialogs.isShowingLocationDialog) {
            callback.invoke(origin, false, false)
            return
        }

        if (hasCoarse && !hasFine) {
            dialogs.showLocationUpgradeDialog(
                origin = uri,
                onUpgrade = {
                    pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                    pendingGeolocationOrigin = origin
                    pendingGeolocationCallback = callback
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        AppConstants.REQUEST_CODE_ACCESS_LOCATION
                    )
                },
                onKeepApproximate = {
                    callback.invoke(origin, true, true)
                },
                onCancel = {
                    callback.invoke(origin, false, false)
                }
            )
            return
        }

        dialogs.showLocationAccessDialog(
            origin = uri,
            isSecure = isSecure,
            onAllowOnce = { continueGeolocationPermissionRequest(origin, callback) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedLocationHost(activity, it) }
                continueGeolocationPermissionRequest(origin, callback)
            },
            onCancel = { callback.invoke(origin, false, false) }
        )
    }

    private fun continueGeolocationPermissionRequest(origin: String?, callback: GeolocationPermissions.Callback) {
        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            callback.invoke(origin, true, true)
        } else {
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
            pendingGeolocationOrigin = origin
            pendingGeolocationCallback = callback
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                AppConstants.REQUEST_CODE_ACCESS_LOCATION
            )
        }
    }

    fun handlePermissionResult(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val granted = hasFine || hasCoarse
        val callback = pendingGeolocationCallback
        val origin = pendingGeolocationOrigin
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
        if (callback != null) {
            callback.invoke(origin, granted, true)
        }
        return granted
    }
}
