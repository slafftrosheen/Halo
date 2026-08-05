package com.halo.driver.heycyan.simulator

import com.halo.core.driver.DeviceCapabilities
import com.halo.core.driver.DeviceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A testing simulator that behaves like a real HeyCyan device.
 * Used for CI testing and development without physical hardware.
 */
class FakeDeviceSimulator {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _events = MutableSharedFlow<DeviceEvent>()
    val events: SharedFlow<DeviceEvent> = _events

    private val _capabilities = MutableStateFlow(
        FakeCapabilities(hasAudio = true, hasCamera = false)
    )
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities

    fun simulateButtonPress() {
        scope.launch {
            _events.emit(FakeButtonEvent(System.currentTimeMillis()))
        }
    }

    fun simulateBatteryDrain() {
        scope.launch {
            // Simulator battery drain logic
        }
    }
}

data class FakeCapabilities(
    val hasAudio: Boolean,
    val hasCamera: Boolean
) : DeviceCapabilities

data class FakeButtonEvent(val timestamp: Long) : DeviceEvent
