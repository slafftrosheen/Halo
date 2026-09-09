package com.halo.app.ui.screens.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicesUiStateTest {
    @Test
    fun `generic device state preserves auto connect preference`() {
        val state = GenericDeviceUiState(
            id = "device-1",
            name = "HeyCyan",
            type = "HEYCYAN_GLASSES",
            isConnected = false,
            battery = "Unknown",
            firmware = "Unknown",
            lastSeen = "Never",
            autoConnect = true,
        )

        assertEquals(true, state.autoConnect)
    }
}
