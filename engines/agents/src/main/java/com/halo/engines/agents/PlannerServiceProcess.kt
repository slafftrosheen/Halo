package com.halo.engines.agents

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.Plan
import com.halo.icl.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.filterIsInstance

class PlannerServiceProcess(
    private val cognitiveBus: CognitiveBus,
    private val executionBus: ExecutionBus
) : Process {
    override val id: String = "service.planner"
    override val name: String = "Planner Service"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            // Filter to only consume Goals from the Cognitive Bus
            cognitiveBus.subscribe(id).filterIsInstance<Goal>().collect { goal ->
                
                val plan = Plan(
                    id = CognitiveId(),
                    created = Clock.System.now(),
                    confidence = goal.confidence * Confidence(0.95f), // Decay slightly
                    provenance = Provenance(id, goal.id),
                    edges = listOf(CognitiveEdge(goal.id, EdgeType.Derived)),
                    intent = goal.desiredState,
                    taskIds = emptyList() // Mock task list
                )
                
                executionBus.publish(id, plan)
            }
        }
        Unit
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
