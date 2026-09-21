package com.kododake.aabrowser.web.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFilterListManagerTest {
    @Test
    fun `accepts public https list urls`() {
        assertTrue(RemoteFilterListManager.isValidRemoteUrl("https://example.com/easylist.txt"))
        assertTrue(RemoteFilterListManager.isValidRemoteUrl("https://filters.example.org:8443/list.txt"))
    }

    @Test
    fun `rejects insecure local and credentialed urls`() {
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("http://example.com/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://localhost/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://192.168.1.2/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://user:pass@example.com/list.txt"))
    }
}
