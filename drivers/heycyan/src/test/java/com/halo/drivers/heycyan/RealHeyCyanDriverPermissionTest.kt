package com.halo.drivers.heycyan

import android.Manifest
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealHeyCyanDriverPermissionTest {
    @Test
    fun bluetoothConnectPermissionIsRequiredOnAndroid12AndLater() {
        var requestedPermission: String? = null

        val allowed = shouldReadBluetoothAdapterName(
            sdkInt = 31,
            permissionChecker = { permission ->
                requestedPermission = permission
                PackageManager.PERMISSION_DENIED
            }
        )

        assertFalse(allowed)
        assertEquals(Manifest.permission.BLUETOOTH_CONNECT, requestedPermission)
    }

    @Test
    fun bluetoothConnectPermissionIsNotRequiredBeforeAndroid12() {
        var permissionWasChecked = false

        val allowed = shouldReadBluetoothAdapterName(
            sdkInt = 30,
            permissionChecker = {
                permissionWasChecked = true
                PackageManager.PERMISSION_DENIED
            }
        )

        assertTrue(allowed)
        assertFalse(permissionWasChecked)
    }
}
