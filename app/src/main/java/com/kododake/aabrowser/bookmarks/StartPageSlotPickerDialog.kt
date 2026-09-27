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

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences

object StartPageSlotPickerDialog {

    fun show(
        activity: AppCompatActivity,
        url: String,
        isHomePageEnabled: Boolean,
        onSlotChanged: () -> Unit
    ) {
        if (isHomePageEnabled) {
            Toast.makeText(activity, R.string.start_page_add_disabled_by_home_page, Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = BrowserPreferences.formatNavigableUrl(url)
        val slots = BrowserPreferences.getStartPageSlots(activity)
        val existingSlot = BrowserPreferences.findStartPageSlot(activity, normalizedUrl)
        var selectedSlot = when {
            existingSlot >= 0 -> existingSlot
            else -> slots.indexOfFirst { it.isNullOrBlank() }.takeIf { it >= 0 } ?: 0
        }
        val slotLabels = Array(BrowserPreferences.MAX_START_PAGE_SITES) { index ->
            val slotUrl = slots.getOrNull(index)
            val summary = if (slotUrl.isNullOrBlank()) {
                activity.getString(R.string.start_page_slot_empty_title)
            } else {
                BookmarkUrlFormatter.displayLabelForUrl(slotUrl)
            }
            "${activity.getString(R.string.start_page_slot_number, index + 1)} - $summary"
        }

        MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.start_page_slot_picker_title)
            .setSingleChoiceItems(slotLabels, selectedSlot) { _, which ->
                selectedSlot = which
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.start_page_slot_picker_save) { _, _ ->
                BrowserPreferences.addBookmark(activity, normalizedUrl)
                BrowserPreferences.setStartPageSlot(activity, selectedSlot, normalizedUrl)
                onSlotChanged()
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.start_page_slot_saved,
                        activity.getString(R.string.start_page_slot_number, selectedSlot + 1)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .apply {
                if (existingSlot >= 0) {
                    setNeutralButton(R.string.start_page_slot_picker_remove) { _, _ ->
                        BrowserPreferences.clearStartPageSlot(activity, existingSlot)
                        onSlotChanged()
                        Toast.makeText(
                            activity,
                            R.string.start_page_slot_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }
}
