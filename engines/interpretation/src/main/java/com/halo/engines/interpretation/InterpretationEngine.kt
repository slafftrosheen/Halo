package com.halo.engines.interpretation

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InterpretationEngine(
    private val interpreterChain: InterpreterChain = InterpreterChain(emptyList())
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.interpretation.core"),
        version = Version(1, 0, 0),
        description = "Core Interpretation Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private var job: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        val eventBus = context.registry.resolve(Services.EventBus)
        
        job = CoroutineScope(context.runtime.scheduler.io).launch {
            eventBus.observe(ObservationCreated::class.java).collect { event ->
                val interpretationContext = InterpretationContext(context)
                val interpretation = interpreterChain.interpret(event.observation, interpretationContext)
                
                if (interpretation != null) {
                    eventBus.publish(InterpretationCreated(interpretation))
                }
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        _state.value = EngineState.Stopped
    }
}
