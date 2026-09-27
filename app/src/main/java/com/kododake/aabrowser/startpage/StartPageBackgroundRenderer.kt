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

package com.kododake.aabrowser.startpage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StartPageBackgroundRenderer(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val resolveThemeColor: (Int) -> Int
) {
    private var loadedStartPageBackgroundUri: String? = null
    val customBackgroundBitmapState = mutableStateOf<Bitmap?>(null)
    val customBackgroundBitmap: Bitmap? get() = customBackgroundBitmapState.value
    private var cachedStartPageGradientSignature: Int = 0
    private var backgroundLoadJob: Job? = null

    fun onDestroy() {
        backgroundLoadJob?.cancel()
        backgroundLoadJob = null
        val old = customBackgroundBitmapState.value
        customBackgroundBitmapState.value = null
        old?.recycle()
        loadedStartPageBackgroundUri = null
    }

    fun refreshStartPageBackground() {
        applyDynamicStartPageGradientBackground()
        val backgroundUri = BrowserPreferences.getStartPageBackgroundUri(activity)

        if (backgroundUri.isNullOrBlank()) {
            clearBackground()
            return
        }

        if (backgroundUri == loadedStartPageBackgroundUri && customBackgroundBitmapState.value != null) {
            binding.startPageBackgroundImage.setImageBitmap(customBackgroundBitmapState.value)
            binding.startPageBackgroundImage.visibility = View.VISIBLE
            return
        }

        backgroundLoadJob?.cancel()
        val uriToLoad = Uri.parse(backgroundUri)
        val metrics = activity.resources.displayMetrics
        val reqWidth = metrics.widthPixels.coerceAtLeast(1)
        val reqHeight = metrics.heightPixels.coerceAtLeast(1)

        backgroundLoadJob = activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledBitmapFromUri(uriToLoad, reqWidth, reqHeight)
            }

            val old = customBackgroundBitmapState.value
            customBackgroundBitmapState.value = bitmap
            old?.recycle()
            loadedStartPageBackgroundUri = if (bitmap != null) backgroundUri else null

            if (bitmap != null) {
                binding.startPageBackgroundImage.setImageBitmap(bitmap)
                binding.startPageBackgroundImage.visibility = View.VISIBLE
            } else {
                binding.startPageBackgroundImage.setImageBitmap(null)
                binding.startPageBackgroundImage.visibility = View.GONE
            }
        }
    }

    private fun clearBackground() {
        val old = customBackgroundBitmapState.value
        customBackgroundBitmapState.value = null
        old?.recycle()
        loadedStartPageBackgroundUri = null
        binding.startPageBackgroundImage.setImageBitmap(null)
        binding.startPageBackgroundImage.visibility = View.GONE
    }

    private fun applyDynamicStartPageGradientBackground() {
        val baseSurface = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val secondaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val tertiaryContainer = resolveThemeColor(com.google.android.material.R.attr.colorTertiaryContainer)

        val isDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val signature = baseSurface xor primaryContainer xor secondaryContainer xor tertiaryContainer xor (if (isDark) 1 else 0)
        if (cachedStartPageGradientSignature == signature) {
            return
        }

        val (linearStart, linearMid, linearEnd) = if (isDark) {
            Triple(
                ColorUtils.blendARGB(baseSurface, secondaryContainer, 0.40f),
                ColorUtils.blendARGB(baseSurface, tertiaryContainer, 0.35f),
                ColorUtils.blendARGB(baseSurface, primaryContainer, 0.40f)
            )
        } else {
            Triple(
                ColorUtils.blendARGB(baseSurface, secondaryContainer, 0.90f),
                ColorUtils.blendARGB(baseSurface, tertiaryContainer, 0.85f),
                ColorUtils.blendARGB(baseSurface, primaryContainer, 0.90f)
            )
        }

        val baseLayer = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(linearStart, linearMid, linearEnd)).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        val density = activity.resources.displayMetrics.density
        val ambientAlpha = if (isDark) (255 * 0.35f).toInt() else (255 * 0.45f).toInt()
        val ambientBlob = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 950f * density
            setGradientCenter(0f, 0f)
            colors = intArrayOf(ColorUtils.setAlphaComponent(primaryContainer, ambientAlpha), Color.TRANSPARENT)
        }

        binding.startPageRoot.background = LayerDrawable(arrayOf(baseLayer, ambientBlob))
        cachedStartPageGradientSignature = signature
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return runCatching {
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun handleStartPageBackgroundPicked(uri: Uri?, onRefreshNeeded: () -> Unit) {
        if (uri == null) return
        if (activity.contentResolver.openInputStream(uri)?.use { true } != true) {
            Toast.makeText(activity, R.string.start_page_background_error, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val prev = BrowserPreferences.getStartPageBackgroundUri(activity)
        BrowserPreferences.setStartPageBackgroundUri(activity, uri.toString())
        if (!prev.isNullOrBlank() && prev != uri.toString()) {
            runCatching { activity.contentResolver.releasePersistableUriPermission(Uri.parse(prev), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        onRefreshNeeded()
        Toast.makeText(activity, R.string.start_page_background_set, Toast.LENGTH_SHORT).show()
    }

    fun clearStartPageBackground(onRefreshNeeded: () -> Unit) {
        val prev = BrowserPreferences.getStartPageBackgroundUri(activity) ?: return
        runCatching { activity.contentResolver.releasePersistableUriPermission(Uri.parse(prev), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        BrowserPreferences.clearStartPageBackgroundUri(activity)
        onRefreshNeeded()
        Toast.makeText(activity, R.string.start_page_background_cleared, Toast.LENGTH_SHORT).show()
    }
}
