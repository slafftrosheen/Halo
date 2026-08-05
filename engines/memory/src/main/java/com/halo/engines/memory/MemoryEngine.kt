package com.halo.engines.memory

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.engines.memory.service.Consolidator
import com.halo.engines.memory.service.ForgettingPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MemoryEngine(
    private val consolidator: Consolidator,
    private val forgettingPolicy: ForgettingPolicy
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.memory.core"),
        version = Version(1, 0, 0),
        description = "Core Memory Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        
        scope.launch {
            // Periodically run consolidation and forgetting
            // while (isActive) {
            //     delay(5.minutes)
            //     consolidator.consolidate()
            //     // forgettingPolicy.apply()
            // }
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }
}
