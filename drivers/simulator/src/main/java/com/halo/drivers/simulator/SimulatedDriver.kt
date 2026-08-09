package com.halo.drivers.simulator

import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic driver that models device lifecycle and device-originated events
 * without Android, Bluetooth, or a physical wearable.
 */
class SimulatedDriver(
    val clock: SimulatedClock = SimulatedClock(),
    initialBatteryLevel: Int = 100,
    initialWearing: Boolean = false
) : Driver {
    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    override val state: StateFlow<DriverState> = _state.asStateFlow()

    private var batteryLevel = initialBatteryLevel
    private var wearing = initialWearing

    val currentBatteryLevel: Int
        get() = batteryLevel

    val isCurrentlyWearing: Boolean
        get() = wearing

    init {
        require(initialBatteryLevel in 0..100) { "Battery level must be between 0 and 100" }
    }

    override suspend fun initialize() {
        _state.value = DriverState.Disconnected
    }

    override suspend fun connect() {
        check(_state.value == DriverState.Disconnected) {
            "Simulator is already connected or connecting"
        }
        _state.value = DriverState.Connecting
        _state.value = DriverState.Connected
        _state.value = DriverState.Ready
        emit(DeviceEvent.Ready(clock.millis()))
        emit(DeviceEvent.WearState(clock.millis(), wearing))
        emit(DeviceEvent.BatteryLevel(clock.millis(), batteryLevel))
    }

    override suspend fun disconnect() {
        _state.value = DriverState.Disconnecting
        _state.value = DriverState.Disconnected
    }

    override suspend fun shutdown() {
        disconnect()
    }

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    suspend fun pressButton(buttonId: Int = 1) {
        checkReady()
        emit(DeviceEvent.ButtonClick(clock.millis(), buttonId))
    }

    suspend fun setBattery(level: Int, charging: Boolean = false) {
        check(level in 0..100) { "Battery level must be between 0 and 100" }
        checkReady()
        batteryLevel = level
        emit(DeviceEvent.BatteryLevel(clock.millis(), level))
        emit(DeviceEvent.ChargingState(clock.millis(), charging))
    }

    suspend fun setWearing(isWorn: Boolean) {
        checkReady()
        wearing = isWorn
        emit(DeviceEvent.WearState(clock.millis(), isWorn))
    }

    suspend fun sendInput(text: String, locale: String? = "en-US") {
        checkReady()
        emit(DeviceEvent.Input(clock.millis(), text, locale))
    }

    private fun checkReady() {
        check(_state.value == DriverState.Ready) {
            "Simulator must be ready before device events can be injected"
        }
    }
}