package com.halo.drivers.heycyan.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanDeviceStateReducerTest {

    private val reducer = HeyCyanDeviceStateReducer()

    @Test
    fun batteryUpdatesState() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.BatteryUpdated(
                level = 87,
                isCharging = true,
            ),
            timestamp = 1000L,
        )

        assertEquals(87, result.battery?.level)
        assertTrue(result.battery?.charging == true)
        assertEquals(1000L, result.lastEventAtMillis)
    }

    @Test
    fun chargingUpdatesState() {
        val initial = HeyCyanDeviceState(
            battery = BatteryState(level = 50, charging = false)
        )

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.BatteryUpdated(
                level = 50,
                isCharging = true,
            ),
            timestamp = 2000L,
        )

        assertEquals(50, result.battery?.level)
        assertTrue(result.battery?.charging == true)
        assertEquals(2000L, result.lastEventAtMillis)
    }

    @Test
    fun versionUpdatesState() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.VersionUpdated(
                hardwareVersion = "v1.0",
                firmwareVersion = "v2.1",
                wifiHardwareVersion = "w1.0",
                wifiFirmwareVersion = "w2.0",
            ),
            timestamp = 3000L,
        )

        assertEquals("v1.0", result.version?.hardware)
        assertEquals("v2.1", result.version?.firmware)
        assertEquals("w1.0", result.version?.wifiHardware)
        assertEquals("w2.0", result.version?.wifiFirmware)
        assertEquals(3000L, result.lastEventAtMillis)
    }

    @Test
    fun mediaCountsUpdateState() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.MediaCountsUpdated(
                photoCount = 10,
                videoCount = 5,
                audioCount = 2,
                mediaType = 1,
            ),
            timestamp = 4000L,
        )

        assertEquals(10, result.mediaCounts?.photos)
        assertEquals(5, result.mediaCounts?.videos)
        assertEquals(2, result.mediaCounts?.audio)
        assertEquals(1, result.mediaCounts?.mediaType)
        assertEquals(4000L, result.lastEventAtMillis)
    }

    @Test
    fun knownModeUpdatesState() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.ModeChanged(
                mode = 0x01,
            ),
            timestamp = 5000L,
        )

        assertEquals(HeyCyanMode.PHOTO, result.mode)
        assertEquals(0x01, result.rawMode)
        assertEquals(5000L, result.lastEventAtMillis)
    }

    @Test
    fun unknownModePreservesRawValue() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.ModeChanged(
                mode = 0x99,
            ),
            timestamp = 6000L,
        )

        assertNull(result.mode)
        assertEquals(0x99, result.rawMode)
        assertEquals(6000L, result.lastEventAtMillis)
    }

    @Test
    fun wifiCredentialsUpdateState() {
        val initial = HeyCyanDeviceState()

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.WiFiHotspotReady(
                ssid = "HeyCyan_AP",
                password = "secret_password",
            ),
            timestamp = 7000L,
        )

        assertEquals("HeyCyan_AP", result.wifiCredentials?.ssid)
        assertEquals("secret_password", result.wifiCredentials?.password)
        assertEquals(7000L, result.lastEventAtMillis)
    }

    @Test
    fun unrelatedEventPreservesExistingState() {
        val initial = HeyCyanDeviceState(
            battery = BatteryState(level = 90, charging = false),
            mode = HeyCyanMode.VIDEO,
        )

        val result = reducer.reduce(
            initial,
            HeyCyanEvent.ButtonPressed(ButtonType.SINGLE_TAP),
            timestamp = 8000L,
        )

        assertEquals(90, result.battery?.level)
        assertEquals(HeyCyanMode.VIDEO, result.mode)
        assertEquals(8000L, result.lastEventAtMillis)
    }

    @Test
    fun reducerIsDeterministic() {
        val initial = HeyCyanDeviceState()
        val event = HeyCyanEvent.BatteryUpdated(level = 75, isCharging = false)

        val result1 = reducer.reduce(initial, event, timestamp = 9000L)
        val result2 = reducer.reduce(initial, event, timestamp = 9000L)

        assertEquals(result1, result2)
    }
}
