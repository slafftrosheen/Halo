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

/** Host adapter. No driver SDK types leave this module. */
class HeyCyanHost(
    private val driver: Driver,
    override val audioOutput: AudioOutput,
    override val id: String = "host.heycyan.glasses"
) : WearableHost {
    override val audioSource: AudioSource = HeyCyanAudioSource(driver)
    override val buttons: Flow<ButtonEvent> = emptyFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    override suspend fun connect() {
        _connectivity.value = ConnectionState.Connecting
        runCatching { driver.connect() }
            .onSuccess {
                _deviceContext.value = _deviceContext.value.copy(connected = true)
                _connectivity.value = ConnectionState.Connected
            }
            .onFailure { error ->
                _connectivity.value = ConnectionState.Failed(error.message ?: "HeyCyan connection failed")
            }
    }

    override suspend fun disconnect() {
        driver.disconnect()
        audioSource.stop()
        _deviceContext.value = _deviceContext.value.copy(connected = false)
        _connectivity.value = ConnectionState.Disconnected
    }
}
