package com.halo.drivers.heycyan

import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.DeviceContext
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.halo.sdk.driver.DeviceEvent

/** Host adapter. No driver SDK types leave this module. */
class HeyCyanHost(
    private val driver: Driver,
    override val audioOutput: AudioOutput,
    override val id: String = "host.heycyan.glasses"
) : WearableHost {
    override val audioSource: AudioSource = HeyCyanAudioSource(driver)
    private val _buttons = MutableSharedFlow<ButtonEvent>()
    override val buttons: Flow<ButtonEvent> = _buttons.asSharedFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    private val scope = CoroutineScope(Dispatchers.IO)
    private var hostJob: Job? = null

    override suspend fun connect() {
        _connectivity.value = ConnectionState.Connecting
        driver.initialize()
        runCatching { driver.connect() }
            .onSuccess {
                _deviceContext.value = _deviceContext.value.copy(connected = true)
                _connectivity.value = ConnectionState.Connected
                
                hostJob = scope.launch {
                    driver.events.collect { event ->
                        when(event) {
                            is DeviceEvent.ButtonClick -> {
                                _buttons.emit(ButtonEvent.Pressed)
                                delay(100)
                                _buttons.emit(ButtonEvent.Released)
                            }
                            is DeviceEvent.BatteryLevel -> {
                                _deviceContext.value = _deviceContext.value.copy(batteryPercent = event.level)
                            }
                            is DeviceEvent.WearState -> {
                                _deviceContext.value = _deviceContext.value.copy(wearing = event.isWorn)
                            }
                            else -> {}
                        }
                    }
                }
            }
            .onFailure { error ->
                _connectivity.value = ConnectionState.Failed(error.message ?: "HeyCyan connection failed")
            }
    }

    override suspend fun disconnect() {
        hostJob?.cancel()
        driver.disconnect()
        audioSource.stop()
        _deviceContext.value = _deviceContext.value.copy(connected = false)
        _connectivity.value = ConnectionState.Disconnected
    }
}
