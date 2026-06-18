package com.kododake.aabrowser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyCoordinatorTest {

    @Test
    fun normalizeBookmarkKey_stripsTrailingSlash() {
        val normalized = UrlSafetyCoordinator.normalizeBookmarkKey("https://example.com/path/")
        assertEquals("https://example.com/path", normalized)
    }

    @Test
    fun isTrustedNavigationTarget_acceptsKnownRoot() {
        assertTrue(UrlSafetyCoordinator.isTrustedNavigationTarget("https://www.google.com/search?q=test"))
    }

    @Test
    fun mergeBookmarkLists_deduplicatesExactMatches() {
        val merged = UrlSafetyCoordinator.mergeBookmarkLists(
            listOf("https://example.com"),
            listOf("https://example.com")
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun recordRedirectHop_allowsInitialHop() {
        val decision = UrlSafetyCoordinator.recordRedirectHop(
            tabId = 99L,
            fromUrl = "https://example.com",
            toUrl = "https://example.com/next"
        )
        assertTrue(decision is UrlSafetyCoordinator.RedirectDecision.Allowed)
        UrlSafetyCoordinator.resetRedirectChain(99L)
    }

    @Test
    fun resolveRedirectTarget_supportsRelativePaths() {
        val resolved = UrlSafetyCoordinator.resolveRedirectTarget(
            currentUrl = "https://example.com/app/",
            nextLocation = "login"
        )
        assertFalse(resolved.isNullOrBlank())
    }
}
