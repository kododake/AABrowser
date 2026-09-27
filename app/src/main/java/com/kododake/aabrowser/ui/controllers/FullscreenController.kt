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

package com.kododake.aabrowser.ui.controllers

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kododake.aabrowser.databinding.ActivityMainBinding

class FullscreenController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val onFullscreenChanged: (Boolean) -> Unit
) {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isImmersiveMode: Boolean = false

    fun isInFullscreen(): Boolean = customView != null

    fun isImmersiveMode(): Boolean = isImmersiveMode

    fun setImmersiveMode(enabled: Boolean) {
        if (isImmersiveMode == enabled) return
        isImmersiveMode = enabled
        updateSystemBars()
    }

    fun updateSystemBars() {
        val shouldHide = (customView != null) || isImmersiveMode
        val controller = WindowInsetsControllerCompat(activity.window, binding.root)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (shouldHide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun enterFullscreen(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
        onPrepareEnter: () -> Unit
    ) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback

        onPrepareEnter()

        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            bringToFront()
        }

        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateSystemBars()
        onFullscreenChanged(true)
    }

    fun exitFullscreen(
        fromWebChrome: Boolean = false,
        onRestoreView: () -> Unit
    ) {
        if (customView == null) return

        binding.fullscreenContainer.apply {
            removeAllViews()
            visibility = View.GONE
        }

        onRestoreView()

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val callback = customViewCallback
        customView = null
        customViewCallback = null

        if (!fromWebChrome) {
            callback?.onCustomViewHidden()
        }
        updateSystemBars()
        onFullscreenChanged(false)
    }
}
