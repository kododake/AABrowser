package com.kododake.aabrowser

import com.kododake.aabrowser.util.LocalNetworkHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHelperTest {

    @Test
    fun isLocalNetworkPermissionMissingError_matchesExpectedErrorText() {
        assertTrue(LocalNetworkHelper.isLocalNetworkPermissionMissingError("net::ERR_LOCAL_NETWORK_PERMISSION_MISSING"))
        assertTrue(LocalNetworkHelper.isLocalNetworkPermissionMissingError("err_local_network_permission_missing"))
        assertFalse(LocalNetworkHelper.isLocalNetworkPermissionMissingError("net::ERR_NAME_NOT_RESOLVED"))
        assertFalse(LocalNetworkHelper.isLocalNetworkPermissionMissingError(null))
    }
}
