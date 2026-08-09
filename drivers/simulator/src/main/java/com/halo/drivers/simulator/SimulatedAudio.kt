package com.halo.drivers.simulator

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SimulatedAudioSource(private val clock: SimulatedClock) : AudioSource {
    private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 128)
    override val frames: Flow<AudioFrame> = _frames.asSharedFlow()

    private var started = false

    override suspend fun start() {
        started = true
    }

    override suspend fun stop() {
        started = false
    }

    suspend fun inject(
        samples: ShortArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        timestamp: Long = clock.millis()
    ) {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channels > 0) { "Channel count must be positive" }
        check(started) { "Audio source must be started before injecting frames" }
        _frames.emit(
            AudioFrame(
                samples = samples.copyOf(),
                sampleRate = sampleRate,
                channels = channels,
                timestamp = timestamp
            )
        )
    }
}

class SimulatedAudioOutput : AudioOutput {
    private val _spokenText = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val spokenText: Flow<String> = _spokenText.asSharedFlow()
    private val _history = mutableListOf<String>()

    val history: List<String>
        get() = synchronized(_history) { _history.toList() }

    override suspend fun speak(text: String) {
        require(text.isNotBlank()) { "Simulated speech output requires non-blank text" }
        synchronized(_history) { _history += text }
        _spokenText.emit(text)
    }
}