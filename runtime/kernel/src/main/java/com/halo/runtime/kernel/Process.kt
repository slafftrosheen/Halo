package com.halo.runtime.kernel

import kotlinx.coroutines.flow.StateFlow

enum class ProcessState {
    Created,
    Running,
    Paused,
    Stopped,
    Crashed
}

interface Process {
    val id: String
    val name: String
    val state: StateFlow<ProcessState>

    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}
