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

package com.kododake.aabrowser.settings.sections

import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.LinearLayout
import android.widget.TextView
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.settings.components.SettingsComponentBuilder
import com.kododake.aabrowser.web.SslErrorHandlerHelper
import java.io.File

object SiteDataSettingsSection {

    fun build(
        context: Context,
        builder: SettingsComponentBuilder
    ): View {
        val siteDataCard = builder.createStyledCard()
        val siteDataInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(builder.dp(16), builder.dp(16), builder.dp(16), builder.dp(16))
        }
        siteDataInner.addView(
            builder.createSectionTitle(
                context.getString(R.string.settings_site_data_title),
                R.drawable.security_24px,
                bottomPaddingDp = 4
            )
        )
        siteDataInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_site_data_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(builder.onSurfaceColor)
            setPadding(0, builder.dp(4), 0, builder.dp(8))
        })

        val clearSitePermissionsButton = builder.createListButton(
            R.id.buttonClearSitePermissions,
            context.getString(R.string.settings_clear_site_permissions),
            R.drawable.lock_reset_24px
        )
        val clearHttpHostsButton = builder.createListButton(
            R.id.buttonClearHttpHosts,
            context.getString(R.string.settings_clear_http_hosts),
            R.drawable.security_24px
        )
        val clearCookiesButton = builder.createListButton(
            R.id.buttonClearCookies,
            context.getString(R.string.settings_clear_cookies),
            R.drawable.delete_forever_24px
        )

        clearSitePermissionsButton.setOnClickListener {
            builder.showConfirmationDialog(
                title = context.getString(R.string.settings_clear_site_permissions_title),
                message = context.getString(R.string.settings_clear_site_permissions_message)
            ) {
                BrowserPreferences.clearSavedSitePermissions(context)
                GeolocationPermissions.getInstance().clearAll()
                WebView.clearClientCertPreferences(null)
                SslErrorHandlerHelper.clearAllowedSslHosts()
                builder.showSuccessDialog(
                    title = context.getString(R.string.settings_clear_site_permissions_success_title),
                    message = context.getString(R.string.settings_clear_site_permissions_success_message)
                )
            }
        }

        clearHttpHostsButton.setOnClickListener {
            builder.showConfirmationDialog(
                title = context.getString(R.string.settings_clear_http_hosts_title),
                message = context.getString(R.string.settings_clear_http_hosts_message)
            ) {
                BrowserPreferences.clearAllowedCleartextHosts(context)
                builder.showSuccessDialog(
                    title = context.getString(R.string.settings_clear_http_hosts_success_title),
                    message = context.getString(R.string.settings_clear_http_hosts_success_message)
                )
            }
        }

        clearCookiesButton.setOnClickListener {
            builder.showConfirmationDialog(
                title = context.getString(R.string.settings_clear_cookies_title),
                message = context.getString(R.string.settings_clear_cookies_message)
            ) {
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(context).apply {
                    clearHttpAuthUsernamePassword()
                }
                runCatching { context.deleteDatabase("webview.db") }
                runCatching { context.deleteDatabase("webviewCache.db") }
                runCatching {
                    val webViewCacheDir = File(context.cacheDir, "org.chromium.android_webview")
                    if (webViewCacheDir.exists()) {
                        webViewCacheDir.deleteRecursively()
                    }
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    builder.showSuccessDialog(
                        title = context.getString(R.string.settings_clear_cookies_success_title),
                        message = context.getString(R.string.settings_clear_cookies_success_message)
                    )
                }
            }
        }

        siteDataInner.addView(clearSitePermissionsButton)
        siteDataInner.addView(clearHttpHostsButton)
        siteDataInner.addView(clearCookiesButton)
        siteDataCard.addView(siteDataInner)

        return siteDataCard
    }
}
