package com.halo.engines.voice

import com.halo.sdk.runtime.Engine

interface VoiceEngine : Engine {
    suspend fun listen()
    suspend fun stopListening()
}
