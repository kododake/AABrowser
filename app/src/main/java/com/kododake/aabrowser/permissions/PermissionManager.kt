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
import android.webkit.PermissionRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kododake.aabrowser.AppConstants
import com.kododake.aabrowser.data.BrowserPreferences

class PermissionManager(private val activity: AppCompatActivity) {

    private val dialogs = PermissionDialogs(activity)
    private val geolocationHandler = GeolocationPermissionHandler(activity, dialogs)

    var pendingPermissionRequest: PermissionRequest? = null
    var pendingSpeechBridgeTabId: Long? = null
    var pendingGeolocationOrigin: String?
        get() = geolocationHandler.pendingGeolocationOrigin
        set(value) { geolocationHandler.pendingGeolocationOrigin = value }
    var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback?
        get() = geolocationHandler.pendingGeolocationCallback
        set(value) { geolocationHandler.pendingGeolocationCallback = value }

    val isShowingCleartextDialog: Boolean get() = dialogs.isShowingCleartextDialog
    val isShowingMicrophoneDialog: Boolean get() = dialogs.isShowingMicrophoneDialog
    val isShowingLocationDialog: Boolean get() = dialogs.isShowingLocationDialog

    fun ensureNotificationPermissionIfNeeded(requestCode: Int) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
    }

    private fun grantableWebPermissionResources(request: PermissionRequest): Array<String> {
        val allowed = setOf(
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE
        )
        return request.resources.filter { it in allowed }.toTypedArray()
    }

    private fun protectedMediaResources(request: PermissionRequest): Array<String> {
        return request.resources
            .filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            .toTypedArray()
    }

    private fun denyAudioButAllowProtectedMediaIfPresent(request: PermissionRequest) {
        val protectedMedia = protectedMediaResources(request)
        if (protectedMedia.isNotEmpty()) {
            request.grant(protectedMedia)
        } else {
            request.deny()
        }
    }

    private fun continueWebPermissionRequest(request: PermissionRequest, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val grantable = grantableWebPermissionResources(request)
            if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
        } else {
            pendingPermissionRequest?.let { oldRequest ->
                denyAudioButAllowProtectedMediaIfPresent(oldRequest)
            }
            pendingPermissionRequest = request
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
        }
    }

    fun handleWebPermissionRequest(request: PermissionRequest, requestCode: Int) {
        val grantable = grantableWebPermissionResources(request)
        if (grantable.isEmpty()) {
            request.deny()
            return
        }

        val origin = runCatching { request.origin }.getOrNull()
        val host = origin?.host?.lowercase()
        val scheme = origin?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"

        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE !in grantable || (isSecure && BrowserPreferences.isHostAllowedMicrophone(activity, host))) {
            continueWebPermissionRequest(request, requestCode)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || dialogs.isShowingMicrophoneDialog) {
            denyAudioButAllowProtectedMediaIfPresent(request)
            return
        }

        dialogs.showMicrophoneAccessDialog(
            origin = origin,
            isSecure = isSecure,
            onAllowOnce = { continueWebPermissionRequest(request, requestCode) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(activity, it) }
                continueWebPermissionRequest(request, requestCode)
            },
            onCancel = { denyAudioButAllowProtectedMediaIfPresent(request) }
        )
    }

    fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?, onPermissionResult: (Boolean) -> Unit) {
        val pageUri = runCatching { pageUrl?.let(Uri::parse) }.getOrNull()
        val host = pageUri?.host?.lowercase()
        val scheme = pageUri?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"
        pendingSpeechBridgeTabId = tabId

        if (isSecure && BrowserPreferences.isHostAllowedMicrophone(activity, host)) {
            continueSpeechRecognitionMicrophoneAccess(onPermissionResult)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || dialogs.isShowingMicrophoneDialog) {
            onPermissionResult(false)
            return
        }

        dialogs.showMicrophoneAccessDialog(
            origin = pageUri,
            isSecure = isSecure,
            onAllowOnce = { continueSpeechRecognitionMicrophoneAccess(onPermissionResult) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(activity, it) }
                continueSpeechRecognitionMicrophoneAccess(onPermissionResult)
            },
            onCancel = {
                onPermissionResult(false)
                pendingSpeechBridgeTabId = null
            }
        )
    }

    private fun continueSpeechRecognitionMicrophoneAccess(onPermissionResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onPermissionResult(true)
            pendingSpeechBridgeTabId = null
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), AppConstants.REQUEST_CODE_RECORD_AUDIO)
        }
    }

    fun handleGeolocationPermissionRequest(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
        geolocationHandler.handleGeolocationPermissionRequest(origin, callback)
    }

    fun showLocationUpgradeDialog(
        origin: Uri?,
        onUpgrade: () -> Unit,
        onKeepApproximate: () -> Unit,
        onCancel: () -> Unit
    ) {
        dialogs.showLocationUpgradeDialog(origin, onUpgrade, onKeepApproximate, onCancel)
    }

    fun showLocationAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        dialogs.showLocationAccessDialog(origin, isSecure, onAllowOnce, onAllowHost, onCancel)
    }

    fun showMicrophoneAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        dialogs.showMicrophoneAccessDialog(origin, isSecure, onAllowOnce, onAllowHost, onCancel)
    }

    fun showCleartextNavigationDialog(
        uri: Uri,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        dialogs.showCleartextNavigationDialog(uri, onAllowOnce, onAllowHost, onCancel)
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onRecordAudioGranted: (Boolean) -> Unit
    ) {
        if (requestCode == AppConstants.REQUEST_CODE_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                if (granted) {
                    val grantable = grantableWebPermissionResources(request)
                    if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
                } else {
                    denyAudioButAllowProtectedMediaIfPresent(request)
                }
            }
            onRecordAudioGranted(granted)
            pendingSpeechBridgeTabId = null
        } else if (requestCode == AppConstants.REQUEST_CODE_ACCESS_LOCATION) {
            geolocationHandler.handlePermissionResult()
        }
    }
}
