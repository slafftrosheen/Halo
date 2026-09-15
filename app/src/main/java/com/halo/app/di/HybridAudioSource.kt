package com.halo.app.di

import com.halo.host.android.audio.AndroidAudioSource
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.AudioSourceState
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

/**
 * Selects the best available microphone at capture start.
 *
 * A connected wearable owns the preferred audio path. When no wearable is
 * connected, Halo remains fully usable through the Android phone microphone.
 * The source is deliberately selected per capture rather than switching a
 * live stream underneath the speech pipeline.
 */
class HybridAudioSource(
    private val wearableHost: WearableHost,
    private val phoneSource: AudioSource = AndroidAudioSource()
) : AudioSource {
    private val _state = MutableStateFlow(AudioSourceState.IDLE)
    override val state: StateFlow<AudioSourceState> = _state.asStateFlow()

    @Volatile
    private var activeSource: AudioSource? = null

    private fun preferredSource(): AudioSource =
        when (wearableHost.connectivity.value) {
            ConnectionState.Connected, ConnectionState.Ready -> wearableHost.audioSource
            else -> phoneSource
        }

    override suspend fun start() {
        val source = preferredSource()
        activeSource = source
        try {
            source.start()
            _state.value = source.state.value
        } catch (error: Throwable) {
            if (source !== phoneSource) {
                phoneSource.start()
                activeSource = phoneSource
                _state.value = phoneSource.state.value
            } else {
                _state.value = AudioSourceState.ERROR
                throw error
            }
        }
    }

    override suspend fun stop() {
        val source = activeSource ?: return
        try {
            source.stop()
        } finally {
            activeSource = null
            _state.value = AudioSourceState.IDLE
        }
    }

    override val frames: Flow<AudioFrame> = flow {
        val source = activeSource ?: return@flow
        source.frames.collect { emit(it) }
    }
}
