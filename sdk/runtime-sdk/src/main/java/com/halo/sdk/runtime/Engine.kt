package com.halo.sdk.runtime

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

data class EngineMetadata(
    val id: Identifier,
    val version: Version,
    val description: String
)

enum class EngineHealth {
    Healthy, Degraded, Failed
}

sealed interface EngineState {
    data object Created : EngineState
    data object Initializing : EngineState
    data object Running : EngineState
    data object Stopped : EngineState
    data class Failed(val reason: String) : EngineState
}

interface Engine {
    val metadata: EngineMetadata
    val health: StateFlow<EngineHealth>
    val state: StateFlow<EngineState>
    val dependencies: Set<KClass<out Engine>>

    suspend fun initialize(context: RuntimeContext)
    suspend fun start()
    suspend fun stop()
}
