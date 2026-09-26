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

import android.content.Context
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile

object UserAgentManager {
    private const val DESKTOP_INITIAL_SCALE_PERCENT = 100
    private const val DESKTOP_BITNESS = 64
    private const val CHROME_VERSION = "149.0.0.0"
    private const val FIREFOX_VERSION = "155.0"
    private const val SAMSUNG_VERSION = "28.0"
    private const val SAMSUNG_CHROMIUM_VERSION = "130.0.0.0"
    private const val ANDROID_PLATFORM_VERSION = "10.0.0"
    private const val WINDOWS_PLATFORM_VERSION = "10.0.0"
    private const val MACOS_PLATFORM_VERSION = "14.0.0"
    private const val IOS_PLATFORM_VERSION = "17.0.0"

    private const val MOBILE_CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Mobile Safari/537.36"
    private const val WINDOWS_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Safari/537.36"
    private const val SAFARI_MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    private const val SAFARI_IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    private const val MOBILE_FIREFOX_UA = "Mozilla/5.0 (Android 10; Mobile; rv:${FIREFOX_VERSION}) Gecko/${FIREFOX_VERSION} Firefox/${FIREFOX_VERSION}"
    private const val WINDOWS_FIREFOX_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:${FIREFOX_VERSION}) Gecko/20100101 Firefox/${FIREFOX_VERSION}"
    private const val MOBILE_EDGE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Mobile Safari/537.36 EdgA/${CHROME_VERSION}"
    private const val WINDOWS_EDGE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Safari/537.36 Edg/${CHROME_VERSION}"
    private const val MOBILE_SAMSUNG_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/${SAMSUNG_VERSION} Chrome/${SAMSUNG_CHROMIUM_VERSION} Mobile Safari/537.36"

    fun isBrowserIdentityApplied(webView: WebView, profile: UserAgentProfile, desktop: Boolean): Boolean {
        return webView.getTag(R.id.webview_user_agent_profile_tag) == profile.storageKey &&
            webView.settings.userAgentString == buildUserAgent(webView.context, profile, desktop)
    }

    fun applyBrowserIdentity(webView: WebView, profile: UserAgentProfile, desktop: Boolean) {
        webView.setTag(R.id.webview_user_agent_profile_tag, profile.storageKey)
        webView.settings.userAgentString = buildUserAgent(webView.context, profile, desktop)
        webView.settings.useWideViewPort = desktop
        webView.settings.loadWithOverviewMode = desktop

        val scalePercent = BrowserPreferences.getGlobalScalePercent(webView.context)
        if (desktop) {
            webView.setInitialScale(0)
            webView.settings.textZoom = scalePercent
        } else {
            webView.setInitialScale(mobileInitialScalePercent(webView))
            webView.settings.textZoom = 100
        }

        applyUserAgentMetadata(webView, profile, desktop)
    }

    fun applyUserAgentMetadata(webView: WebView, profile: UserAgentProfile, desktop: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            return
        }

        val metadata = when (profile) {
            UserAgentProfile.ANDROID_CHROME -> buildChromeUserAgentMetadata(desktop)
            UserAgentProfile.SAFARI -> buildSafariLikeUserAgentMetadata(desktop)
            UserAgentProfile.FIREFOX -> buildFirefoxUserAgentMetadata(desktop)
            UserAgentProfile.EDGE -> buildChromiumUserAgentMetadata(desktop, "Microsoft Edge", CHROME_VERSION, CHROME_VERSION)
            UserAgentProfile.SAMSUNG_INTERNET -> if (desktop) {
                buildChromeUserAgentMetadata(desktop = true)
            } else {
                buildChromiumUserAgentMetadata(desktop = false, "Samsung Internet", SAMSUNG_VERSION, SAMSUNG_CHROMIUM_VERSION)
            }
            UserAgentProfile.CUSTOM -> UserAgentMetadata.Builder()
                .setMobile(buildUserAgent(webView.context, profile, desktop).contains("Mobile"))
                .build()
        }
        WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata)
    }

    fun buildUserAgent(context: Context, profile: UserAgentProfile, desktop: Boolean): String {
        return when (profile) {
            UserAgentProfile.ANDROID_CHROME -> if (desktop) WINDOWS_CHROME_UA else MOBILE_CHROME_UA
            UserAgentProfile.SAFARI -> if (desktop) SAFARI_MAC_UA else SAFARI_IOS_UA
            UserAgentProfile.FIREFOX -> if (desktop) WINDOWS_FIREFOX_UA else MOBILE_FIREFOX_UA
            UserAgentProfile.EDGE -> if (desktop) WINDOWS_EDGE_UA else MOBILE_EDGE_UA
            UserAgentProfile.SAMSUNG_INTERNET -> if (desktop) WINDOWS_CHROME_UA else MOBILE_SAMSUNG_UA
            UserAgentProfile.CUSTOM -> {
                val customUa = BrowserPreferences.getCustomUserAgent(context)
                if (customUa.isBlank()) {
                    if (desktop) WINDOWS_CHROME_UA else MOBILE_CHROME_UA
                } else {
                    customUa
                }
            }
        }
    }

    fun buildChromeUserAgentMetadata(desktop: Boolean): UserAgentMetadata {
        return buildChromiumUserAgentMetadata(desktop, "Google Chrome", CHROME_VERSION, CHROME_VERSION)
    }

    fun buildChromiumUserAgentMetadata(
        desktop: Boolean,
        brand: String,
        brandVersion: String,
        chromiumVersion: String
    ): UserAgentMetadata {
        return UserAgentMetadata.Builder()
            .setBrandVersionList(chromiumBrandVersions(brand, brandVersion, chromiumVersion))
            .setFullVersion(brandVersion)
            .setPlatform(if (desktop) "Windows" else "Android")
            .setPlatformVersion(if (desktop) WINDOWS_PLATFORM_VERSION else ANDROID_PLATFORM_VERSION)
            .setArchitecture(if (desktop) "x86" else "")
            .setModel("")
            .setMobile(!desktop)
            .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
            .setWow64(false)
            .build()
    }

    fun buildFirefoxUserAgentMetadata(desktop: Boolean): UserAgentMetadata {
        return UserAgentMetadata.Builder()
            .setPlatform(if (desktop) "Windows" else "Android")
            .setPlatformVersion(if (desktop) WINDOWS_PLATFORM_VERSION else ANDROID_PLATFORM_VERSION)
            .setArchitecture(if (desktop) "x86" else "")
            .setModel("")
            .setMobile(!desktop)
            .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
            .setWow64(false)
            .build()
    }

    fun buildSafariLikeUserAgentMetadata(desktop: Boolean): UserAgentMetadata {
        return UserAgentMetadata.Builder()
            .setPlatform(if (desktop) "macOS" else "iOS")
            .setPlatformVersion(if (desktop) MACOS_PLATFORM_VERSION else IOS_PLATFORM_VERSION)
            .setArchitecture(if (desktop) "arm" else "")
            .setModel("")
            .setMobile(!desktop)
            .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
            .setWow64(false)
            .build()
    }

    fun chromiumBrandVersions(
        brand: String,
        brandVersion: String,
        chromiumVersion: String
    ): List<UserAgentMetadata.BrandVersion> {
        return listOf(
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand)
                .setMajorVersion(brandVersion.substringBefore('.'))
                .setFullVersion(brandVersion)
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Chromium")
                .setMajorVersion(chromiumVersion.substringBefore('.'))
                .setFullVersion(chromiumVersion)
                .build()
        )
    }

    private fun mobileInitialScalePercent(webView: WebView): Int {
        return (webView.context.resources.displayMetrics.density * 100).toInt()
    }
}
