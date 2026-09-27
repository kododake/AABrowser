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

package com.kododake.aabrowser.bookmarks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.google.android.material.textview.MaterialTextView
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.SiteIconCache

object BookmarkIconUtils {

    fun prefetchSiteIcon(context: Context, url: String?, onIconLoaded: ((Bitmap?) -> Unit)? = null) {
        if (!BookmarkUrlFormatter.isActiveWebsiteUrl(url)) {
            return
        }
        SiteIconCache.prefetchIconIfNeeded(context, url) { bitmap ->
            if (bitmap != null) {
                onIconLoaded?.invoke(bitmap)
            }
        }
    }

    fun resolveCachedSiteIcon(context: Context, url: String?, onPrefetched: ((Bitmap?) -> Unit)? = null): Bitmap? {
        val cached = SiteIconCache.getCachedIcon(context, url)
        if (cached == null && !url.isNullOrBlank()) {
            prefetchSiteIcon(context, url, onPrefetched)
        }
        return cached
    }

    fun createSiteIconBadge(
        context: Context,
        url: String?,
        sizeDp: Float,
        cornerRadiusDp: Float,
        paddingDp: Float,
        backgroundColor: Int,
        plusColor: Int = 0,
        showAddOnEmptyUrl: Boolean = false
    ): View {
        val density = context.resources.displayMetrics.density
        val cachedIcon = resolveCachedSiteIcon(context, url)
        return FrameLayout(context).apply {
            val sizePx = (sizeDp * density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusDp * density
                setColor(backgroundColor)
            }
            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val p = (paddingDp * density).toInt()
                setPadding(p, p, p, p)
                if (cachedIcon != null) {
                    setImageBitmap(cachedIcon)
                } else {
                    setImageResource(R.drawable.public_24px)
                }
                visibility = if (showAddOnEmptyUrl && url.isNullOrBlank()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            })
            if (showAddOnEmptyUrl) {
                addView(MaterialTextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    gravity = Gravity.CENTER
                    text = "+"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(plusColor)
                    visibility = if (url.isNullOrBlank()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                })
            }
        }
    }
}
