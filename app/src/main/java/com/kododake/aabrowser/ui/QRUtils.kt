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

package com.kododake.aabrowser.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for generating and caching QR codes.
 */
object QRUtils {

    private val qrCache = LruCache<String, Bitmap>(10)

    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        val cacheKey = "${content}_$size"
        val cached = qrCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val isBlack = bitMatrix.get(x, y)
                    bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
                }
            }
            
            qrCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }


    suspend fun generateQrCodeAsync(content: String, size: Int = 512): Bitmap? {
        val cacheKey = "${content}_$size"
        val cached = qrCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return withContext(Dispatchers.Default) {
            generateQrCode(content, size)
        }
    }
}
