package com.halo.engines.agents

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.Observation
import com.halo.icl.ontology.Thought
import com.halo.icl.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.datetime.Clock

import com.halo.engines.reasoning.providers.NativeLlamaProvider

class ReasoningServiceProcess(
    private val perceptionBus: PerceptionBus,
    private val cognitiveBus: CognitiveBus,
    private val provider: NativeLlamaProvider
) : Process {
    override val id: String = "service.reasoning"
    override val name: String = "Reasoning Service"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            perceptionBus.subscribe(id).filterIsInstance<Observation>().collect { observation ->
                // Example reasoning loop with NativeLlamaProvider
                val prompt = "Observation: ${observation.modality} -> ${observation.rawData}\nWhat should I do?"
                
                provider.generate(prompt).collect { token ->
                    // For now, emit each token as a partial thought, or accumulate it
                    // In a real system, you accumulate and emit the final thought
                    val thought = Thought(
                        id = CognitiveId(),
                        created = Clock.System.now(),
                        confidence = observation.confidence,
                        provenance = Provenance(id, observation.id),
                        edges = listOf(CognitiveEdge(observation.id, EdgeType.Derived)),
                        content = token
                    )
                    cognitiveBus.publish(id, thought)
                }
            }
        }
        Unit
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
