package com.halo.runtime.kernel

import kotlinx.coroutines.flow.StateFlow
import com.halo.sdk.runtime.MetricsProvider
import com.halo.sdk.runtime.EngineSnapshot

enum class ProcessState {
    Created,
    Running,
    Paused,
    Stopped,
    Crashed
}

interface Process : MetricsProvider {
    val id: String
    val name: String
    val state: StateFlow<ProcessState>

    fun engineSnapshot(): EngineSnapshot

    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}
