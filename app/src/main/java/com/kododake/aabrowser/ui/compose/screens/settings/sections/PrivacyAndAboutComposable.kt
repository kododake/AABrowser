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

package com.kododake.aabrowser.ui.compose.screens.settings.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.AppConstants
import com.kododake.aabrowser.BuildConfig
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile
import com.kododake.aabrowser.ui.compose.components.ExpressiveListItem
import com.kododake.aabrowser.ui.compose.components.easterEggReleaseDetector
import com.kododake.aabrowser.ui.compose.screens.dialogs.CustomUserAgentDialog
import com.kododake.aabrowser.ui.compose.screens.dialogs.ExpressiveConfirmationDialog
import com.kododake.aabrowser.ui.compose.screens.dialogs.ExpressiveSingleChoiceDialog
import com.kododake.aabrowser.web.CrossOriginIsolation
import com.kododake.aabrowser.web.SslErrorHandlerHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivacyAndAboutComposable(
    context: Context,
    onDrmL3EnforcerChanged: () -> Unit = {},
    onCrossOriginIsolationChanged: () -> Unit = {},
    onUserAgentChanged: () -> Unit = {},
    onEasterEggHoldStart: () -> Unit = {},
    onEasterEggHoldEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var userAgent by remember { mutableStateOf(BrowserPreferences.getUserAgentProfile(context)) }
    var customUserAgent by remember { mutableStateOf(BrowserPreferences.getCustomUserAgent(context)) }
    var drmL3Enabled by remember { mutableStateOf(BrowserPreferences.isDrmL3EnforcerEnabled(context)) }
    var coiEnabled by remember { mutableStateOf(BrowserPreferences.isCrossOriginIsolationEnabled(context)) }
    var showUaDialog by remember { mutableStateOf(false) }
    var showCustomUaDialog by remember { mutableStateOf(false) }
    var showClearCookiesDialog by remember { mutableStateOf(false) }
    var showClearPermissionsDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_site_data_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        val isCustomUa = userAgent == UserAgentProfile.CUSTOM
        val siteDataCount = if (isCustomUa) 6 else 5
        var siteDataIndex = 0

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            ExpressiveListItem(
                headline = stringResource(R.string.settings_user_agent),
                supportingText = stringResource(userAgent.titleRes),
                leadingIcon = Icons.Rounded.Devices,
                index = siteDataIndex++,
                count = siteDataCount,
                onClick = { showUaDialog = true }
            )

            if (isCustomUa) {
                ExpressiveListItem(
                    headline = stringResource(R.string.settings_user_agent_custom_hint),
                    supportingText = customUserAgent.ifBlank { stringResource(R.string.settings_user_agent_custom_empty_fallback) },
                    leadingIcon = Icons.Rounded.Edit,
                    index = siteDataIndex++,
                    count = siteDataCount,
                    onClick = { showCustomUaDialog = true }
                )
            }

            ExpressiveListItem(
                headline = stringResource(R.string.settings_drm_l3_enforcer),
                supportingText = stringResource(R.string.settings_drm_l3_enforcer_description),
                leadingIcon = Icons.Rounded.Security,
                index = siteDataIndex++,
                count = siteDataCount,
                trailingContent = {
                    Switch(
                        checked = drmL3Enabled,
                        onCheckedChange = { checked ->
                            drmL3Enabled = checked
                            BrowserPreferences.setDrmL3EnforcerEnabled(context, checked)
                            onDrmL3EnforcerChanged()
                        }
                    )
                },
                onClick = {
                    val next = !drmL3Enabled
                    drmL3Enabled = next
                    BrowserPreferences.setDrmL3EnforcerEnabled(context, next)
                    onDrmL3EnforcerChanged()
                }
            )

            ExpressiveListItem(
                headline = stringResource(R.string.settings_cross_origin_isolation),
                supportingText = stringResource(R.string.settings_cross_origin_isolation_description),
                leadingIcon = Icons.Rounded.SportsEsports,
                index = siteDataIndex++,
                count = siteDataCount,
                trailingContent = {
                    Switch(
                        checked = coiEnabled,
                        onCheckedChange = { checked ->
                            coiEnabled = checked
                            BrowserPreferences.setCrossOriginIsolationEnabled(context, checked)
                            onCrossOriginIsolationChanged()
                        }
                    )
                },
                onClick = {
                    val next = !coiEnabled
                    coiEnabled = next
                    BrowserPreferences.setCrossOriginIsolationEnabled(context, next)
                    onCrossOriginIsolationChanged()
                }
            )

            ExpressiveListItem(
                headline = stringResource(R.string.settings_clear_site_permissions),
                supportingText = stringResource(R.string.settings_clear_site_permissions_title),
                leadingIcon = Icons.Rounded.LockReset,
                index = siteDataIndex++,
                count = siteDataCount,
                onClick = { showClearPermissionsDialog = true }
            )

            ExpressiveListItem(
                headline = stringResource(R.string.settings_clear_cookies),
                supportingText = stringResource(R.string.settings_site_data_description),
                leadingIcon = Icons.Rounded.Delete,
                index = siteDataIndex,
                count = siteDataCount,
                onClick = { showClearCookiesDialog = true }
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_license),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            ExpressiveListItem(
                headline = stringResource(R.string.open_source_view_licenses),
                supportingText = stringResource(R.string.open_source_licenses_title),
                leadingIcon = Icons.Rounded.Code,
                index = 0,
                count = 3,
                onClick = {
                    try {
                        val activityClass = Class.forName("com.google.android.gms.oss.licenses.OssLicensesMenuActivity")
                        val intent = Intent(context, activityClass)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            ExpressiveListItem(
                headline = stringResource(R.string.settings_license),
                supportingText = stringResource(R.string.settings_license_description),
                leadingIcon = Icons.Rounded.Info,
                index = 1,
                count = 3,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )

            ExpressiveListItem(
                headline = stringResource(R.string.app_name),
                supportingText = "v${BuildConfig.VERSION_NAME} • ${AppConstants.GITHUB_REPO_URL}",
                leadingIcon = Icons.Rounded.Info,
                index = 2,
                count = 3,
                modifier = Modifier.easterEggReleaseDetector(onEasterEggHoldEnd),
                onLongClick = onEasterEggHoldStart,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.GITHUB_REPO_URL))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )
        }
    }

    if (showUaDialog) {
        ExpressiveSingleChoiceDialog(
            title = stringResource(R.string.settings_user_agent),
            items = UserAgentProfile.entries,
            selectedItem = userAgent,
            onItemSelected = { profile ->
                if (profile != userAgent) {
                    userAgent = profile
                    BrowserPreferences.setUserAgentProfile(context, profile)
                    onUserAgentChanged()
                }
                if (profile == UserAgentProfile.CUSTOM) {
                    showCustomUaDialog = true
                }
            },
            onDismiss = { showUaDialog = false },
            itemLabel = { profile ->
                stringResource(profile.titleRes)
            }
        )
    }

    if (showCustomUaDialog) {
        CustomUserAgentDialog(
            initialValue = customUserAgent,
            onConfirm = { newUa ->
                BrowserPreferences.setCustomUserAgent(context, newUa)
                customUserAgent = BrowserPreferences.getCustomUserAgent(context)
                onUserAgentChanged()
            },
            onDismiss = { showCustomUaDialog = false }
        )
    }

    if (showClearPermissionsDialog) {
        ExpressiveConfirmationDialog(
            title = stringResource(R.string.settings_clear_site_permissions_title),
            message = stringResource(R.string.settings_clear_site_permissions_message),
            isDestructive = true,
            confirmButtonText = stringResource(android.R.string.ok),
            onConfirm = {
                BrowserPreferences.clearSavedSitePermissions(context)
                GeolocationPermissions.getInstance().clearAll()
                WebView.clearClientCertPreferences(null)
                SslErrorHandlerHelper.clearAllowedSslHosts()
                Toast.makeText(context, R.string.settings_clear_site_permissions_success_title, Toast.LENGTH_SHORT).show()
                showClearPermissionsDialog = false
            },
            onDismiss = { showClearPermissionsDialog = false }
        )
    }

    if (showClearCookiesDialog) {
        ExpressiveConfirmationDialog(
            title = stringResource(R.string.settings_clear_cookies_title),
            message = stringResource(R.string.settings_clear_cookies_message),
            isDestructive = true,
            confirmButtonText = stringResource(android.R.string.ok),
            onConfirm = {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CrossOriginIsolation.clear(context)
                Toast.makeText(context, R.string.settings_clear_cookies_success_title, Toast.LENGTH_SHORT).show()
                showClearCookiesDialog = false
            },
            onDismiss = { showClearCookiesDialog = false }
        )
    }
}
