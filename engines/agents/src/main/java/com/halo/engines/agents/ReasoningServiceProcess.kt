package com.halo.engines.agents

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.icl.ontology.Observation
import com.halo.icl.ontology.Thought
import com.halo.icl.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.datetime.Clock
import com.halo.sdk.runtime.EngineSnapshot
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineMetrics
import java.time.Duration
import java.time.Instant
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiContext

/**
 * Legacy kernel reasoning bridge kept for compatibility with the microkernel.
 *
 * The actual AI selection is delegated to the same AiProviderRouter used by
 * CognitiveSession, so this process can never bypass the user's provider/model
 * selection and accidentally invoke a different inference provider.
 */
class ReasoningServiceProcess(
    private val perceptionBus: PerceptionBus,
    private val cognitiveBus: CognitiveBus,
    private val aiProviderRouter: AiProviderRouter
) : Process {
    override val id: String = "service.reasoning"
    override val name: String = "Reasoning Service"

    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    private val _metrics = MutableStateFlow(EngineMetrics(null, Duration.ZERO, 0, 0, 0.0))
    override val metrics: StateFlow<EngineMetrics> = _metrics
    private var eventsProcessed = 0L

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running

        launch {
            perceptionBus.subscribe(id)
                .filterIsInstance<Observation>()
                .collect { observation ->
                    try {
                        val request = AiRequest(
                            text = "Observation: ${observation.modality} -> ${observation.rawData}\nWhat should I do?"
                        )
                        val response = aiProviderRouter.execute(request, AiContext.EMPTY)

                        val thought = Thought(
                            id = CognitiveId(),
                            created = Clock.System.now(),
                            confidence = observation.confidence,
                            provenance = Provenance(id, observation.id),
                            edges = listOf(CognitiveEdge(observation.id, EdgeType.Derived)),
                            content = response.text
                        )
                        cognitiveBus.publish(id, thought)
                        eventsProcessed++
                        _metrics.value = _metrics.value.copy(eventsProcessed = eventsProcessed)
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Provider/routing failures must not tear down the kernel process.
                        eventsProcessed++
                        _metrics.value = _metrics.value.copy(eventsProcessed = eventsProcessed)
                    }
                }
        }
        Unit
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }

    override fun engineSnapshot(): EngineSnapshot {
        return EngineSnapshot(
            id = name,
            state = when (state.value) {
                ProcessState.Created -> EngineState.Unknown
                ProcessState.Running -> EngineState.Running
                ProcessState.Paused -> EngineState.Unknown
                ProcessState.Stopped -> EngineState.Unknown
                ProcessState.Crashed -> EngineState.Failed("Crashed")
            },
            health = EngineHealth.Healthy,
            metrics = metrics.value,
            lastActivity = Instant.now()
        )
    }
}
