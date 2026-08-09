package com.halo.drivers.simulator

import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.DeviceContext
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Host adapter for [SimulatedDriver]. It intentionally exposes the same host
 * contract as physical devices so runtime tests do not need special runtime code.
 */
class SimulatedWearableHost(
    val driver: SimulatedDriver = SimulatedDriver(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    override val id: String = "host.simulator"
) : WearableHost {
    val simulatedAudioSource = SimulatedAudioSource(driver.clock)
    val simulatedAudioOutput = SimulatedAudioOutput()

    override val audioSource: AudioSource = simulatedAudioSource
    override val audioOutput: AudioOutput = simulatedAudioOutput

    private val _buttons = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 64)
    override val buttons: Flow<ButtonEvent> = _buttons.asSharedFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext.asStateFlow()

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity.asStateFlow()

    private var eventJob: Job? = null

    override suspend fun connect() {
        check(eventJob == null) { "Simulator host is already connected" }
        _connectivity.value = ConnectionState.Connecting
        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.events.collect(::handle)
        }
        try {
            driver.connect()
            _deviceContext.value = _deviceContext.value.copy(
                connected = true,
                batteryPercent = driver.currentBatteryLevel,
                wearing = driver.isCurrentlyWearing
            )
            _connectivity.value = ConnectionState.Connected
            yield()
        } catch (error: Throwable) {
            eventJob?.cancel()
            eventJob = null
            _connectivity.value = ConnectionState.Failed(error.message ?: "Simulator connection failed")
            throw error
        }
    }

    override suspend fun disconnect() {
        driver.disconnect()
        eventJob?.cancel()
        eventJob = null
        simulatedAudioSource.stop()
        _deviceContext.value = _deviceContext.value.copy(connected = false)
        _connectivity.value = ConnectionState.Disconnected
    }

    fun close() {
        eventJob?.cancel()
        eventJob = null
    }

    suspend fun pressButton(buttonId: Int = 1) {
        driver.pressButton(buttonId)
        yield()
    }

    suspend fun setBattery(level: Int, charging: Boolean = false) {
        driver.setBattery(level, charging)
        yield()
    }

    suspend fun setWearing(isWorn: Boolean) {
        driver.setWearing(isWorn)
        yield()
    }

    suspend fun injectAudio(
        samples: ShortArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        timestamp: Long = driver.clock.millis()
    ) = simulatedAudioSource.inject(samples, sampleRate, channels, timestamp)

    private suspend fun handle(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.ButtonClick -> {
                _buttons.emit(ButtonEvent.Pressed)
                _buttons.emit(ButtonEvent.Released)
            }
            is DeviceEvent.BatteryLevel -> {
                _deviceContext.value = _deviceContext.value.copy(batteryPercent = event.level)
            }
            is DeviceEvent.ChargingState -> {
                _deviceContext.value = _deviceContext.value.copy(charging = event.isCharging)
            }
            is DeviceEvent.WearState -> {
                _deviceContext.value = _deviceContext.value.copy(wearing = event.isWorn)
            }
            else -> Unit
        }
    }
}