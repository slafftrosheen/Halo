package com.halo.app.ui.screens.devices

import com.halo.sdk.runtime.embodiment.DeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesCardDensityTest {
    private fun saved(connected: Boolean, battery: String = "Unknown",
        seen: String = "Never") = GenericDeviceUiState(
        id = "saved", name = "Glasses", kind = DeviceKind.HEYCYAN_GLASSES,
        isConnected = connected, connectionStateText = if (connected) "Ready" else "Saved",
        autoConnect = true, battery = battery, firmware = "v1", lastSeen = seen,
    )

    @Test fun disconnectedListNeverSurfacesLiveBatteryOrCapabilities() {
        assertTrue(deviceListSecondaryFacts(saved(false, "82%", "Never")).isEmpty())
        assertEquals(listOf("Seen" to "Last seen yesterday"),
            deviceListSecondaryFacts(saved(false, "82%", "Last seen yesterday")))
    }
    @Test fun confirmedReadyDeviceShowsRealBatteryWithoutDuplicateNow() {
        assertEquals(listOf("Battery" to "82%"),
            deviceListSecondaryFacts(saved(true, "82%", "Now")))
    }
}
