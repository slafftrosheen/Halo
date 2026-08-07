package com.halo.host.heycyan

import com.halo.sdk.host.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class HeyCyanHost : WearableHost {
    override val id: String = "host.heycyan"

    // TODO: Connect to HeyCyan SDK properly
    override val audioSource: AudioSource = object : AudioSource {
        override val frames: Flow<AudioFrame> = emptyFlow()
        override suspend fun start() {}
        override suspend fun stop() {}
    }

    override val audioOutput: AudioOutput = object : AudioOutput {
        override suspend fun speak(text: String) {}
    }

    override val buttons: Flow<ButtonEvent> = emptyFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext(wearing = false, connected = false))
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    override suspend fun connect() {
        _connectivity.value = ConnectionState.Connecting
        // TODO: Initialize SDK connection
        _connectivity.value = ConnectionState.Connected
        _deviceContext.value = _deviceContext.value.copy(connected = true)
    }

    override suspend fun disconnect() {
        _connectivity.value = ConnectionState.Disconnected
        _deviceContext.value = _deviceContext.value.copy(connected = false)
    }
}
