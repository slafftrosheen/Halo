package com.halo.drivers.simulator

import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SimulatedWearableHostTest {
    @Test
    fun `host exposes deterministic lifecycle and device state`() = runTest {
        val clock = SimulatedClock(initialMillis = 1_000L)
        val host = SimulatedWearableHost(
            driver = SimulatedDriver(clock = clock, initialBatteryLevel = 85),
            scope = this
        )

        host.connect()
        assertEquals(ConnectionState.Connected, host.connectivity.value)
        assertEquals(true, host.deviceContext.value.connected)
        assertEquals(85, host.deviceContext.value.batteryPercent)

        val button = async { host.buttons.first() }
        runCurrent()
        host.pressButton()
        assertEquals(ButtonEvent.Pressed, withTimeout(1.seconds) { button.await() })

        host.setBattery(72, charging = true)
        assertEquals(72, host.deviceContext.value.batteryPercent)
        assertTrue(host.deviceContext.value.charging)

        host.disconnect()
        assertEquals(ConnectionState.Disconnected, host.connectivity.value)
        assertEquals(false, host.deviceContext.value.connected)
    }

    @Test
    fun `audio is copied and output is recorded`() = runTest {
        val clock = SimulatedClock(initialMillis = 500L)
        val host = SimulatedWearableHost(SimulatedDriver(clock = clock), scope = this)
        host.connect()
        host.simulatedAudioSource.start()

        val frame = async { host.audioSource.frames.first() }
        runCurrent()
        val samples = shortArrayOf(1, 2, 3)
        host.injectAudio(samples)
        samples[0] = 99

        assertEquals(1, frame.await().samples[0].toInt())
        host.audioOutput.speak("Hello from Halo")
        assertEquals(listOf("Hello from Halo"), host.simulatedAudioOutput.history)
        host.disconnect()
    }
}