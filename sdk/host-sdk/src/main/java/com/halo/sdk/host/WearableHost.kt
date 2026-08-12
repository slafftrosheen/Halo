package com.halo.sdk.host

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A physical or virtual device host exposed to the host-agnostic runtime. */
interface WearableHost {
    val id: String
    val audioSource: AudioSource
    val audioOutput: AudioOutput
    val buttons: Flow<ButtonEvent>
    val deviceContext: StateFlow<DeviceContext>
    val connectivity: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()
}

interface AudioSource {
    suspend fun start()
    suspend fun stop()
    val frames: Flow<AudioFrame>
}

interface AudioOutput {
    suspend fun speak(text: String)
}

data class AudioFrame(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
    val timestamp: Long
)

data class AudioBuffer(val frames: List<AudioFrame>)

enum class ButtonEvent {
    Pressed,
    Released
}

data class DeviceContext(
    val name: String? = null,
    val batteryPercent: Int? = null,
    val wearing: Boolean = false,
    val connected: Boolean = false,
    val charging: Boolean = false,
    val temperatureCelsius: Float? = null,
    val capabilities: List<String> = emptyList()
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

sealed interface ObservationSource {
    data object AndroidPhoneMic : ObservationSource
    data object BluetoothHeadset : ObservationSource
    data object HeyCyanGlasses : ObservationSource
    data object Replay : ObservationSource
    data object Debug : ObservationSource
}
