package com.example.core.engine

interface Engine {
    suspend fun initialize()
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun destroy()
}
