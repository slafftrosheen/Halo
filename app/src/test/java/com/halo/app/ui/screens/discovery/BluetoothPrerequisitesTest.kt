package com.halo.app.ui.screens.discovery

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BluetoothPrerequisitesTest {

    @Test
    @Config(sdk = [30])
    fun preAndroid12DiscoveryUsesLocationPermission() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requiredBluetoothDiscoveryPermissions()
        )
    }

    @Test
    @Config(sdk = [33])
    fun android12PlusDiscoveryUsesNearbyBluetoothPermissions() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            requiredBluetoothDiscoveryPermissions()
        )
    }
}
