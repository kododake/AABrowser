package com.kododake.aabrowser

import com.kododake.aabrowser.data.BrowserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPageSponsorRotationTest {

    @Test
    fun shouldResetOnUpgrade_resetsOnlyOnVersionChange() {
        assertTrue(BrowserPreferences.shouldResetOnUpgrade(-1L, 10L))
        assertTrue(BrowserPreferences.shouldResetOnUpgrade(10L, 11L))
        assertFalse(BrowserPreferences.shouldResetOnUpgrade(10L, 10L))
        assertFalse(BrowserPreferences.shouldResetOnUpgrade(10L, -1L))
    }
}
