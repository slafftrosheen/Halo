package com.halo.host.android

import com.halo.host.android.audio.AndroidAudioSource
import com.halo.sdk.runtime.AudioOutput
import com.halo.sdk.runtime.AudioSource
import com.halo.sdk.runtime.ButtonEvent
import com.halo.sdk.runtime.ConnectionState
import com.halo.sdk.runtime.DeviceContext
import com.halo.sdk.runtime.WearableHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Android phone host. Android APIs remain behind this host boundary. */
class AndroidWearableHost(
    override val audioSource: AudioSource = AndroidAudioSource(),
    override val audioOutput: AudioOutput,
    override val id: String = "host.android.phone"
) : WearableHost {
    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    override val buttons: Flow<ButtonEvent> = emptyFlow()

    override suspend fun connect() {
        _deviceContext.value = _deviceContext.value.copy(connected = true)
        _connectivity.value = ConnectionState.Connected
    }

    override suspend fun disconnect() {
        audioSource.stop()
        _deviceContext.value = _deviceContext.value.copy(connected = false)
        _connectivity.value = ConnectionState.Disconnected
    }
}
