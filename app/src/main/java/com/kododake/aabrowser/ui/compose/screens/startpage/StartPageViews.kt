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

package com.kododake.aabrowser.ui.compose.screens.startpage

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object StartPageViews {

    fun createStartPageContent(
        context: Context,
        slots: List<StartPageSlotUi>,
        hasResumePage: Boolean,
        sponsorsQrBitmap: Bitmap?,
        customBackgroundBitmapState: State<Bitmap?>? = null,
        customBackgroundBitmapProvider: () -> Bitmap? = { null },
        isNavigatingState: State<Boolean>? = null,
        callbacks: StartPageScreenCallbacks = StartPageScreenCallbacks()
    ): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isNavigating = isNavigatingState?.value ?: false
                val customBackgroundBitmap = customBackgroundBitmapState?.value ?: customBackgroundBitmapProvider()
                StartPageScreen(
                    context = context,
                    slots = slots,
                    hasResumePage = hasResumePage,
                    sponsorsQrBitmap = sponsorsQrBitmap,
                    customBackgroundBitmap = customBackgroundBitmap,
                    isNavigating = isNavigating,
                    callbacks = callbacks
                )
            }
        }
    }
}
