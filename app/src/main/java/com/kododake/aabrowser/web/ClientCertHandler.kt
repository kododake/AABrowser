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

package com.kododake.aabrowser.web

import android.app.Activity
import android.content.Context
import android.security.KeyChain
import android.webkit.ClientCertRequest

object ClientCertHandler {
    private const val PREF_NAME = "client_cert_prefs"

    fun handleClientCertRequest(activity: Activity, request: ClientCertRequest) {
        val host = request.host
        val port = request.port
        val keyTypes = request.keyTypes
        val principals = request.principals

        val prefKey = if (port >= 0) "$host:$port" else host

        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encryptedAlias = prefs.getString(prefKey, null)
        val savedAlias = encryptedAlias?.let { CryptoHelper.decrypt(it) }

        if (savedAlias != null) {
            Thread {
                try {
                    val privateKey = KeyChain.getPrivateKey(activity, savedAlias)
                    val chain = KeyChain.getCertificateChain(activity, savedAlias)
                    if (privateKey != null && chain != null) {
                        activity.runOnUiThread {
                            request.proceed(privateKey, chain)
                        }
                        return@Thread
                    }
                } catch (_: Exception) {

                }
                prefs.edit().remove(prefKey).apply()
                activity.runOnUiThread {
                    showCertSelectionDialog(activity, request, prefs, prefKey, host, keyTypes, principals, port)
                }
            }.start()
        } else {
            showCertSelectionDialog(activity, request, prefs, prefKey, host, keyTypes, principals, port)
        }
    }

    private fun showCertSelectionDialog(
        activity: Activity,
        request: ClientCertRequest,
        prefs: android.content.SharedPreferences,
        prefKey: String,
        host: String,
        keyTypes: Array<String>?,
        principals: Array<java.security.Principal>?,
        port: Int
    ) {
        KeyChain.choosePrivateKeyAlias(
            activity,
            { alias ->
                if (alias == null) {
                    request.cancel()
                    return@choosePrivateKeyAlias
                }
                
                val encrypted = CryptoHelper.encrypt(alias)
                if (encrypted != null) {
                    prefs.edit().putString(prefKey, encrypted).apply()
                }

                Thread {
                    try {
                        val privateKey = KeyChain.getPrivateKey(activity, alias)
                        val chain = KeyChain.getCertificateChain(activity, alias)
                        if (privateKey != null && chain != null) {
                            activity.runOnUiThread {
                                request.proceed(privateKey, chain)
                            }
                        } else {
                            activity.runOnUiThread {
                                request.cancel()
                            }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            request.cancel()
                        }
                    }
                }.start()
            },
            keyTypes,
            principals,
            host,
            port,
            null
        )
    }
}
