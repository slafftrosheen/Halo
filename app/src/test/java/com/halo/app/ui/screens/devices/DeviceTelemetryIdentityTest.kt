package com.halo.app.ui.screens.devices

import com.halo.drivers.heycyan.protocol.*
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTelemetryIdentityTest {
    private val saved = DeviceRecord(DeviceIdentity(
        "AA:BB:CC:00:11:22", "HEYCYAN_GLASSES", "My glasses"))
    private fun driver(address: String?, ready: Boolean = true) = HeyCyanDeviceState(
        connection = if (ready) HeyCyanConnectionState.READY else HeyCyanConnectionState.DISCONNECTED,
        transportIdentity = address?.let { HeyCyanTransportIdentity(it) },
    )
    @Test fun savedDisconnectAndTransportConnectionAreNotLiveTelemetry() {
        assertFalse(deviceHasMatchedHeyCyanTelemetry(saved, null,
            driver("AA:BB:CC:00:11:22")))
        assertFalse(deviceHasMatchedHeyCyanTelemetry(saved, SessionState.CONNECTED,
            driver("AA:BB:CC:00:11:22")))
        assertFalse(deviceHasMatchedHeyCyanTelemetry(saved, SessionState.READY,
            driver("AA:BB:CC:00:11:22", ready = false)))
    }
    @Test fun onlyExactLiveTargetIsAdmissible() {
        assertFalse(deviceHasMatchedHeyCyanTelemetry(saved, SessionState.READY,
            driver("CC:DD:EE:FF:00:11")))
        assertFalse(deviceHasMatchedHeyCyanTelemetry(saved, SessionState.READY,
            driver(null)))
        assertTrue(deviceHasMatchedHeyCyanTelemetry(saved, SessionState.READY,
            driver("aa:bb:cc:00:11:22")))
    }
}
