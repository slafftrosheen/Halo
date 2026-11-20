package com.halo.drivers.heycyan.protocol

import org.junit.Assert.*
import org.junit.Test

class HeyCyanTransportIdentityTest {
    @Test fun selectedIdentitySurvivesHandshakeAndTelemetryUntilDisconnect() {
        val store = HeyCyanDeviceStateStore()
        store.beginConnection("AA:BB:CC:00:11:22")
        assertEquals(HeyCyanConnectionState.SCANNING, store.state.value.connection)
        assertEquals("AA:BB:CC:00:11:22", store.state.value.transportIdentity?.controlBleAddress)
        assertNull(store.state.value.transportIdentity?.classicAudioAddress)
        store.updateConnection(HeyCyanConnectionState.CONNECTING)
        store.updateConnection(HeyCyanConnectionState.READY)
        store.apply(HeyCyanEvent.BatteryUpdated(87, false), 123L)
        assertEquals(87, store.state.value.battery?.level)
        assertEquals("AA:BB:CC:00:11:22", store.state.value.transportIdentity?.controlBleAddress)
        store.updateConnection(HeyCyanConnectionState.DISCONNECTED)
        assertNull(store.state.value.transportIdentity)
    }

    @Test fun selectingAnotherDeviceClearsPreviousTelemetryAndRequiresAnotherHandshake() {
        val store = HeyCyanDeviceStateStore()
        store.beginConnection("AA:BB:CC:00:11:22")
        store.updateConnection(HeyCyanConnectionState.READY)
        store.apply(HeyCyanEvent.BatteryUpdated(87, false))
        store.beginConnection("CC:DD:EE:00:11:22")
        assertEquals(HeyCyanConnectionState.SCANNING, store.state.value.connection)
        assertEquals("CC:DD:EE:00:11:22", store.state.value.transportIdentity?.controlBleAddress)
        assertNull(store.state.value.battery)
        assertNull(store.state.value.lastEventAtMillis)
        store.updateConnection(HeyCyanConnectionState.ERROR)
        assertNull(store.state.value.transportIdentity)
    }

    @Test fun malformedIdentityCannotCreateATransportOwner() {
        val store = HeyCyanDeviceStateStore()
        assertThrows(IllegalArgumentException::class.java) { store.beginConnection("A2") }
        assertEquals(HeyCyanConnectionState.DISCONNECTED, store.state.value.connection)
        assertNull(store.state.value.transportIdentity)
    }
}
