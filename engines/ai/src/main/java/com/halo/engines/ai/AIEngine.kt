package com.halo.engines.ai

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AI Engine interface for cognitive tasks.
 */
interface AIEngineInterface : Engine {
    suspend fun reason(input: String): String
    suspend fun summarize(input: String): String
    suspend fun generate(prompt: String): String
}

/**
 * Implementation of AIEngine that delegates to AiProviderRouter.
 */
class AIEngine(
    private val router: AiProviderRouter
) : AIEngineInterface {

    override val metadata = EngineMetadata(
        id = Identifier("engine.ai"),
        version = Version(1, 1, 0),
        description = "Multimodal AI Processing Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()
    
    private var context: EngineContext? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
        _health.value = EngineHealth.Healthy
    }

    override suspend fun start() {
        _state.value = EngineState.Running
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }

    override suspend fun reason(input: String): String {
        return router.execute(AiRequest(text = input, systemContext = "Reason about this.")).text
    }

    override suspend fun summarize(input: String): String {
        return router.execute(AiRequest(text = input, systemContext = "Summarize this.")).text
    }

    override suspend fun generate(prompt: String): String {
        return router.execute(AiRequest(text = prompt)).text
    }
}
