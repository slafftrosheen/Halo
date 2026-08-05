package com.halo.engines.speech
import kotlinx.coroutines.flow.Flow

interface AudioSource {
    suspend fun start()

    /**
     * Stops capture after an attempted or active start.
     *
     * Implementations must make this safe to call when startup fails partway through.
     */
    suspend fun stop()

    val frames: Flow<AudioFrame>
}
