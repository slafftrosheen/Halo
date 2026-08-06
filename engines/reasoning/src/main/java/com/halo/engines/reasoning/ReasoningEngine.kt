package com.halo.engines.reasoning

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.cognition.CognitiveCycle
import com.halo.runtime.cognition.blackboard.Blackboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter

import com.halo.engines.reasoning.strategy.DefaultReasoningStrategy
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.GoalCreated
import com.halo.sdk.runtime.EventBus

class ReasoningEngine(
    private val strategy: DefaultReasoningStrategy,
    private val blackboard: Blackboard,
    private val eventBus: EventBus
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.reasoning.core"),
        version = Version(1, 0, 0),
        description = "Core Reasoning Engine"
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
            // Subscribe to InterpretationKnowledge from Blackboard
            blackboard.stream().filter { it.payload is InterpretationKnowledge }.collect { knowledge ->
                val interpretation = (knowledge.payload as InterpretationKnowledge).interpretation
                val goal = strategy.generateGoal(interpretation)
                
                // Publish GoalCreated event
                eventBus.publish(GoalCreated(goal = goal))
            }
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }
}
