package com.halo.core.engine

import com.halo.core.runtime.RuntimeContext
import kotlinx.coroutines.flow.StateFlow

interface Engine {
    val id: String
    val health: StateFlow<EngineHealth>
    val state: StateFlow<EngineState>

    suspend fun initialize(context: RuntimeContext)
    suspend fun start()
    suspend fun stop()
    suspend fun shutdown()
}

enum class EngineHealth {
    HEALTHY, DEGRADED, FAILING, OFFLINE
}

enum class EngineState {
    CREATED, INITIALIZED, READY, BUSY, STOPPED, ERROR
}
