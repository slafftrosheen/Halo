package com.halo.engines.diagnostics

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.services.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiagnosticsEngine(
    private val events: EventBus? = null,
    private val commands: CommandBus? = null
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.diagnostics"),
        version = Version(1, 0, 0),
        description = "System Diagnostics and Telemetry"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus, Services.CommandBus)

    private lateinit var context: EngineContext
    private var job: Job? = null
    
    private lateinit var eventBus: EventBus
    private lateinit var commandBus: CommandBus

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        this.eventBus = events ?: context.registry.resolve(Services.EventBus)
        this.commandBus = commands ?: context.registry.resolve(Services.CommandBus)
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running

        job = CoroutineScope(context.runtime.scheduler.io).launch {
            launch {
                eventBus.events().collect { event ->
                    context.runtime.logger.info("Diagnostics", "Event: ${event::class.simpleName}")
                }
            }
            launch {
                commandBus.commands().collect { command ->
                    context.runtime.logger.info("Diagnostics", "Command: ${command::class.simpleName}")
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
