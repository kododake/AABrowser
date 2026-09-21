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

    @Test
    fun `default subscriptions use public https urls and point at the uAssets mirror`() {
        val defaults = RemoteFilterListManager.defaultSubscriptions()
        assertTrue(defaults.size == 6)
        defaults.forEach { subscription ->
            assertTrue(subscription.url, RemoteFilterListManager.isValidRemoteUrl(subscription.url))
            assertTrue(subscription.url, subscription.url.startsWith("https://ublockorigin.github.io/uAssets/"))
            assertTrue(subscription.builtIn && subscription.enabled)
        }
        assertTrue(defaults.map { it.id }.toSet().size == defaults.size)
    }

    @Test
    fun `rejects spoofed and malformed hosts`() {
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://127.0.0.1:8080/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://10.1.2.3/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("https://[::1]/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("ftp://example.com/list.txt"))
        assertFalse(RemoteFilterListManager.isValidRemoteUrl("not a url"))
        assertTrue(RemoteFilterListManager.isValidRemoteUrl("https://localhost.example.com/list.txt"))
    }
}
