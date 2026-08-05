package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.InterpretationCreated
import com.halo.engines.interpretation.api.InterpretationEngine
import com.halo.engines.interpretation.api.InterpretationResult
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.runtime.cognition.blackboard.InterpretationPayload
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import com.halo.runtime.cognition.blackboard.ObservationPayload
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.services.Services
import com.halo.sdk.runtime.EngineContext
import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineMetadata
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.runtime.Source
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

open class DefaultInterpretationEngine(
    private val pipeline: RulePipeline,
    private val blackboard: Blackboard,
    private val contextAssembler: ContextAssembler
) : InterpretationEngine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.interpretation.core"),
        version = Version(1, 0, 0),
        description = "Core Interpretation Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(
        Services.EventBus,
        Services.Blackboard
    )

    private lateinit var context: EngineContext
    private var job: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        check(context.registry.resolve(Services.Blackboard) === blackboard) {
            "InterpretationEngine must use the registered Blackboard instance"
        }
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        val eventBus = context.registry.resolve(Services.EventBus)

        job = CoroutineScope(context.runtime.scheduler.io).launch {
            eventBus.observe(ObservationCreated::class.java).collect { event ->
                try {
                    val processed = process(event.observation)
                    val interpretation = processed.result.interpretation ?: return@collect
                    val knowledge = processed.knowledge ?: return@collect

                    eventBus.publish(
                        InterpretationCreated(
                            interpretation = interpretation,
                            knowledgeId = knowledge.id
                        )
                    )
                    _health.value = EngineHealth.Healthy
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    _health.value = EngineHealth.Degraded
                    context.runtime.logger.error(
                        category = "interpretation",
                        message = "Failed to process an observation",
                        throwable = exception
                    )
                }
            }
        }
    }

    override suspend fun interpret(
        observation: Observation
    ): InterpretationResult = process(observation).result

    override suspend fun stop() {
        job?.cancel()
        job = null
        _state.value = EngineState.Stopped
    }

    private suspend fun process(
        observation: Observation
    ): ProcessedInterpretation {
        blackboard.publish(observation.asKnowledge())

        val result = pipeline.execute(contextAssembler.build(observation))
        val interpretation = result.interpretation
            ?: return ProcessedInterpretation(result, null)
        val knowledge = Knowledge(
            id = KnowledgeId(UUID.randomUUID().toString()),
            payload = InterpretationPayload(interpretation),
            confidence = interpretation.confidence,
            source = KnowledgeSource.RULE,
            createdAt = Instant.fromEpochMilliseconds(observation.timestamp)
        )
        blackboard.publish(knowledge)

        return ProcessedInterpretation(result, knowledge)
    }

    private fun Observation.asKnowledge(): Knowledge = Knowledge(
        id = KnowledgeId(UUID.randomUUID().toString()),
        payload = ObservationPayload(this),
        confidence = Confidence(
            lexical = confidence,
            contextual = confidence,
            semantic = confidence
        ),
        source = when (source) {
            Source.MICROPHONE -> KnowledgeSource.AUDIO
            Source.CAMERA -> KnowledgeSource.CAMERA
            Source.GPS -> KnowledgeSource.GPS
            Source.USER -> KnowledgeSource.USER
            Source.BLUETOOTH,
            Source.OS,
            Source.SYSTEM -> KnowledgeSource.SENSOR
        },
        createdAt = Instant.fromEpochMilliseconds(timestamp)
    )

    private data class ProcessedInterpretation(
        val result: InterpretationResult,
        val knowledge: Knowledge?
    )
}
