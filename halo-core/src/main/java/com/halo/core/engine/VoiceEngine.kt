package com.halo.core.engine

interface VoiceEngine : Engine {
    suspend fun listen()
    suspend fun stopListening()
}
