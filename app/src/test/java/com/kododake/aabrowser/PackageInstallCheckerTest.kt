package com.kododake.aabrowser

import android.content.pm.PackageManager
import com.kododake.aabrowser.util.PackageInstallChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallCheckerTest {

    @Test
    fun isPackageInstalled_returnsTrueWhenPackageExists() {
        val result = PackageInstallChecker.isPackageInstalled(AppConstants.AA_ANYTHING_PACKAGE_NAME) {
            Any()
        }
        assertTrue(result)
    }

    @Test
    fun isPackageInstalled_returnsFalseWhenNameNotFoundExceptionThrown() {
        val result = PackageInstallChecker.isPackageInstalled(AppConstants.AA_ANYTHING_PACKAGE_NAME) {
            throw PackageManager.NameNotFoundException()
        }
        assertFalse(result)
    }

    @Test
    fun isPackageInstalled_returnsFalseWhenLookupReturnsNullOrBlankPackageName() {
        assertFalse(PackageInstallChecker.isPackageInstalled(AppConstants.AA_ANYTHING_PACKAGE_NAME) { null })
        assertFalse(PackageInstallChecker.isPackageInstalled("   ") { Any() })
    }
}
