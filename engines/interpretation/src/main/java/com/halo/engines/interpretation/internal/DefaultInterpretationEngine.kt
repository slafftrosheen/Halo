package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.InterpretationCreated
import com.halo.engines.interpretation.InterpretationBuilt
import com.halo.engines.interpretation.InterpretationPublished
import com.halo.engines.interpretation.InterpretationStarted
import com.halo.engines.interpretation.RuleMatched
import com.halo.engines.interpretation.api.InterpretationEngine
import com.halo.engines.interpretation.api.InterpretationResult
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.runtime.cognition.blackboard.EntityKnowledge
import com.halo.runtime.cognition.blackboard.IntentKnowledge
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import com.halo.runtime.cognition.blackboard.ObservationKnowledge
import com.halo.runtime.cognition.blackboard.SpeechKnowledge
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
    private val eventBus: com.halo.sdk.runtime.EventBus
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

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private lateinit var context: EngineContext
    private var job: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
        context.runtime.logger.info(
            category = "interpretation",
            message = "Loaded ${pipeline.ruleCount} speech rules"
        )
    }

    override suspend fun start() {
        _state.value = EngineState.Running

        job = CoroutineScope(context.runtime.scheduler.io).launch {
            eventBus.observe(ObservationCreated::class.java).collect { event ->
                val speech = event.observation.payload as? SpeechRecognizedPayload
                if (speech == null) {
                    blackboard.publish(event.observation.asKnowledge())
                    return@collect
                }
                try {
                    eventBus.publish(InterpretationStarted(id = UUID.randomUUID().toString(), transcript = speech.transcript))
                    val processed = process(event.observation)
                    for (match in processed.result.matches) {
                        eventBus.publish(
                            RuleMatched(
                                id = UUID.randomUUID().toString(),
                                ruleName = match.ruleName,
                                confidence = match.confidence,
                                entityCount = match.entities.size,
                                diagnostics = match.diagnostics?.let { mapOf("info" to it) } ?: emptyMap()
                            )
                        )
                        context.runtime.logger.info(
                            category = "interpretation",
                            message = "Rule=${match.ruleName} confidence=${match.confidence} " +
                                "latencyMs=${match.latencyMillis} entities=${match.entities.size}"
                        )
                    }

                    for (execution in processed.result.executions) {
                        eventBus.publish(execution)
                    }

                    eventBus.publish(InterpretationBuilt(id = UUID.randomUUID().toString(), interpretation = processed.result.interpretation))
                    eventBus.publish(InterpretationPublished(id = UUID.randomUUID().toString(), knowledgeId = processed.knowledge.id))

                    eventBus.publish(
                        InterpretationCreated(
                            id = UUID.randomUUID().toString(),
                            interpretation = processed.result.interpretation,
                            knowledgeId = processed.knowledge.id
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
        val speechKnowledge = observation.asSpeechKnowledge()
        if (speechKnowledge != null) {
            blackboard.publish(speechKnowledge)
        } else {
            blackboard.publish(observation.asKnowledge())
        }

        val result = pipeline.execute(
            com.halo.runtime.cognition.context.CognitiveContext(
                observation = observation,
                workingMemory = com.halo.runtime.cognition.workingmemory.WorkingMemorySnapshot(
                    currentGoal = null,
                    activeTask = null,
                    latestObservation = observation,
                    latestInterpretation = null,
                    focusedPerson = null,
                    currentLocation = null,
                    recentKnowledge = emptyList()
                ),
                goals = emptyList(),
                tasks = emptyList(),
                timestamp = Instant.fromEpochMilliseconds(observation.timestamp)
            )
        )
        val interpretation = result.interpretation.copy(metadata = result.interpretation.metadata + buildMap {
            put("observationId", observation.id.value)
            put("inputSource", observation.origin?.source?.toString() ?: observation.source.name)
            observation.sourceDeviceId?.let { put("sourceDeviceId", it) }
            observation.metadata["captureId"]?.let { put("captureId", it.toString()) }
            observation.metadata["inputRoute"]?.let { put("inputRoute", it.toString()) }
        })
        val createdAt = Instant.fromEpochMilliseconds(observation.timestamp)

        blackboard.publish(
            Knowledge(
                id = KnowledgeId(UUID.randomUUID().toString()),
                payload = IntentKnowledge(
                    intent = interpretation.intent,
                    confidence = interpretation.confidence
                ),
                confidence = com.halo.runtime.cognition.blackboard.Confidence(interpretation.confidence, interpretation.confidence, interpretation.confidence),
                source = KnowledgeSource.RULE,
                createdAt = createdAt
            )
        )
        blackboard.publish(
            Knowledge(
                id = KnowledgeId(UUID.randomUUID().toString()),
                payload = EntityKnowledge(interpretation.entities),
                confidence = com.halo.runtime.cognition.blackboard.Confidence(interpretation.confidence, interpretation.confidence, interpretation.confidence),
                source = KnowledgeSource.RULE,
                createdAt = createdAt
            )
        )
        val knowledge = Knowledge(
            id = KnowledgeId(UUID.randomUUID().toString()),
            payload = InterpretationKnowledge(interpretation),
            confidence = com.halo.runtime.cognition.blackboard.Confidence(interpretation.confidence, interpretation.confidence, interpretation.confidence),
            source = KnowledgeSource.RULE,
            createdAt = createdAt
        )
        blackboard.publish(knowledge)

        return ProcessedInterpretation(result, knowledge)
    }

    private fun Observation.asKnowledge(): Knowledge = Knowledge(
        id = KnowledgeId(UUID.randomUUID().toString()),
        payload = ObservationKnowledge(this),
        confidence = Confidence(
            lexical = confidence,
            contextual = 1f,
            semantic = 1f
        ),
        source = when (source) {
            Source.MICROPHONE -> KnowledgeSource.AUDIO
            Source.CAMERA -> KnowledgeSource.CAMERA
            Source.GPS -> KnowledgeSource.GPS
            Source.USER -> KnowledgeSource.USER
            Source.ROBOT,
            Source.BLUETOOTH,
            Source.OS,
            Source.SYSTEM -> KnowledgeSource.SENSOR
        },
        createdAt = Instant.fromEpochMilliseconds(timestamp)
    )

    private fun Observation.asSpeechKnowledge(): Knowledge? {
        val speech = payload as? SpeechRecognizedPayload ?: return null
        val recognitionConfidence = confidence.coerceIn(0f, 1f)
        val knowledgeConfidence = Confidence(
            lexical = recognitionConfidence,
            contextual = 1f,
            semantic = 1f
        )
        return Knowledge(
            id = KnowledgeId(UUID.randomUUID().toString()),
            payload = SpeechKnowledge(
                transcript = speech.transcript,
                confidence = recognitionConfidence,
                language = speech.language,
                timestamp = timestamp
            ),
            confidence = knowledgeConfidence,
            // Keyboard input reuses the same transcript payload as speech so it
            // can enter the same interpreter. Preserve the actual input source
            // instead of poisoning the blackboard with fake AUDIO provenance.
            source = if (source == Source.USER) KnowledgeSource.USER else KnowledgeSource.AUDIO,
            createdAt = Instant.fromEpochMilliseconds(timestamp)
        )
    }

    private data class ProcessedInterpretation(
        val result: InterpretationResult,
        val knowledge: Knowledge
    )
}
