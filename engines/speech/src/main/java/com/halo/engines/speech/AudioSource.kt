package com.halo.engines.speech
import kotlinx.coroutines.flow.Flow

interface AudioSource {
    suspend fun start()
    suspend fun stop()
    val frames: Flow<AudioFrame>
}
