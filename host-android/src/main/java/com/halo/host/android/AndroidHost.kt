package com.halo.host.android

import com.halo.host.android.audio.AndroidAudioSource
import com.halo.sdk.host.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class AndroidHost : WearableHost {
    override val id: String = "host.android"

    override val audioSource: AudioSource = AndroidAudioSource()

    override val audioOutput: AudioOutput = object : AudioOutput {
        override suspend fun speak(text: String) {}
    }

    override val buttons: Flow<ButtonEvent> = emptyFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext(wearing = true, connected = true))
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    override suspend fun connect() {}
    override suspend fun disconnect() {}
}
