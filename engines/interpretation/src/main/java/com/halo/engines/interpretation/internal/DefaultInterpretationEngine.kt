package com.halo.engines.interpretation.internal

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.clarification.*
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.defaultResponseDelivery
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
import com.halo.runtime.cognition.blackboard.InteractionOrigin
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
import com.halo.sdk.runtime.SpeechInputRoute
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

open class DefaultInterpretationEngine(
    private val pipeline: RulePipeline,
    private val blackboard: Blackboard,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    private val clarificationCoordinator: ClarificationCoordinator? = null,
    private val agentRunCoordinator: AgentRunCoordinator? = null,
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
    private val processMutex = Mutex()
    private var clarificationExpiryJob: Job? = null
    private var pendingInterpretation: com.halo.runtime.cognition.blackboard.Interpretation? = null

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
        clarificationExpiryJob?.cancel()
        processMutex.withLock {
            clarificationCoordinator?.cancel("Interpretation stopped")
            pendingInterpretation?.metadata?.get("agentRunId")?.let {
                agentRunCoordinator?.cancel(AgentRunId(it), "Interpretation stopped")
            }
            pendingInterpretation = null
        }
        _state.value = EngineState.Stopped
    }

    private suspend fun process(
        observation: Observation
    ): ProcessedInterpretation = processMutex.withLock {
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
        val speechRoute = observation.metadata["inputRoute"] as? SpeechInputRoute
        val origin = InteractionOrigin(
            source = observation.source,
            sourceDeviceId = observation.sourceDeviceId,
            captureId = observation.metadata["captureId"]?.toString(),
            speechRoute = speechRoute
        )

        var interpretation = result.interpretation.copy(
            origin = origin,
            metadata = result.interpretation.metadata + buildMap {
            put("observationId", observation.id.value)
            put("interactionId", observation.metadata["interactionId"]?.toString() ?: observation.interactionId)
            put("inputSource", observation.origin?.source?.toString() ?: observation.source.name)
            observation.sourceDeviceId?.let { put("sourceDeviceId", it) }
            observation.metadata["captureId"]?.let { put("captureId", it.toString()) }
            observation.metadata["inputRoute"]?.let { put("inputRoute", it.toString()) }
        })
        val coordinator = clarificationCoordinator
        val prior = pendingInterpretation
        if (coordinator != null && prior != null) {
            val now = java.time.Instant.now()
            val pending = coordinator.pending.value
            if (pending == null || !now.isBefore(pending.expiresAt)) {
                coordinator.cancel("Expired")
                prior.metadata["agentRunId"]?.let { agentRunCoordinator?.cancel(AgentRunId(it), "Clarification expired") }
                pendingInterpretation = null
                clarificationExpiryJob?.cancel()
            } else if (prior.origin?.sourceDeviceId == interpretation.origin?.sourceDeviceId &&
                prior.origin?.source == interpretation.origin?.source && prior.origin?.speechRoute == interpretation.origin?.speechRoute) {
                val reply = interpretation.transcript.trim().lowercase().trimEnd('.', '!', '?')
                if (reply in setOf("cancel", "cancel it", "never mind", "nevermind")) {
                    coordinator.cancel("User cancelled")
                    prior.metadata["agentRunId"]?.let { agentRunCoordinator?.cancel(AgentRunId(it), "User cancelled") }
                    pendingInterpretation = null
                    clarificationExpiryJob?.cancel()
                    interpretation = interpretation.copy(metadata = interpretation.metadata + ("clarificationOnly" to "true"))
                    publishClarificationResponse(prior, "Cancelled.")
                } else when (val resolution = coordinator.consume(interpretation.transcript, now)) {
                    is ClarificationResolution.Resolved -> {
                        interpretation = prior.copy(entities = resolution.entities,
                            metadata = prior.metadata - "clarificationOnly")
                        prior.metadata["agentRunId"]?.let { agentRunCoordinator?.transition(AgentRunId(it), AgentRunState.PLANNING) }
                        pendingInterpretation = null
                        clarificationExpiryJob?.cancel()
                    }
                    else -> Unit // Unrelated input continues normally and cannot fill the pending field.
                }
            }
        }
        if (coordinator != null && (interpretation.intent is SetAlarmIntent || interpretation.intent is ReminderIntent) &&
            interpretation.metadata["clarificationOnly"] != "true") {
            val reminderTime = interpretation.entities.filterIsInstance<ReminderEntity>().firstOrNull()?.temporalExpression
            val unresolved = interpretation.entities.filterIsInstance<UnresolvedTimeEntity>().firstOrNull()
                ?: if (interpretation.intent is ReminderIntent && reminderTime != null &&
                    TemporalExpressionParser().parse(reminderTime) is TemporalParseResult.Incomplete)
                    UnresolvedTimeEntity(reminderTime, TemporalField.AM_PM, 1f) else null
            val needsTime = unresolved != null || (interpretation.intent is SetAlarmIntent &&
                interpretation.entities.filterIsInstance<TimeEntity>().none { it.value is TemporalValue.TimeOfDay })
            if (needsTime) {
                pendingInterpretation?.metadata?.get("agentRunId")?.let {
                    agentRunCoordinator?.cancel(AgentRunId(it), "Replaced by a new clarification")
                }
                val interactionId = InteractionId(interpretation.metadata.getValue("interactionId"))
                val route = interpretation.origin?.speechRoute
                val target = if (route is SpeechInputRoute.Wearable) SpeechOutputTarget.Wearable(route.deviceId, route.displayName) else SpeechOutputTarget.Phone
                val runId = agentRunCoordinator?.start(AgentRequest(interactionId, interpretation.transcript, observation.origin,
                    speechOutputTarget = target, sourceCaptureId = interpretation.origin?.captureId))
                val now = java.time.Instant.now()
                val expression = unresolved?.originalText.orEmpty()
                val question = if (unresolved?.missingField == TemporalField.AM_PM)
                    "$expression in the morning or evening?" else "What exact time should I use?"
                runId?.let {
                    agentRunCoordinator?.transition(it, AgentRunState.PLANNING)
                    agentRunCoordinator?.recordStep(AgentStep.ClarificationRequested(AgentStepId(UUID.randomUUID().toString()), it, now, question, "time"))
                    agentRunCoordinator?.transition(it, AgentRunState.WAITING_FOR_CLARIFICATION)
                }
                interpretation = interpretation.copy(metadata = interpretation.metadata + buildMap {
                    put("clarificationOnly", "true")
                    runId?.let { put("agentRunId", it.value) }
                })
                coordinator.request(PendingClarification(UUID.randomUUID().toString(), interactionId,
                    interpretation.intent, "time", interpretation.entities, interpretation.transcript, now, now.plusSeconds(120)))
                pendingInterpretation = interpretation
                clarificationExpiryJob?.cancel()
                if (this::context.isInitialized) clarificationExpiryJob = CoroutineScope(context.runtime.scheduler.io).launch {
                    delay(120_000)
                    processMutex.withLock {
                        if (coordinator.pending.value?.interactionId == interactionId) {
                            coordinator.cancel("Expired")
                            pendingInterpretation = null
                            runId?.let { agentRunCoordinator?.cancel(it, "Clarification expired") }
                        }
                    }
                }
                publishClarificationResponse(interpretation, question)
            }
        }
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

        val updatedResult = result.copy(interpretation = interpretation)
        return@withLock ProcessedInterpretation(updatedResult, knowledge)
    }

    private suspend fun publishClarificationResponse(
        interpretation: com.halo.runtime.cognition.blackboard.Interpretation, text: String,
    ) {
        val id = interpretation.metadata.getValue("interactionId")
        val delivery = interpretation.source.defaultResponseDelivery()
        eventBus.publish(AssistantResponsePublished(text = text, delivery = delivery, transactionId = id))
        if (delivery == ResponseDelivery.SPEECH_AND_VISUAL) {
            val route = interpretation.origin?.speechRoute
            val target = if (route is SpeechInputRoute.Wearable) SpeechOutputTarget.Wearable(route.deviceId, route.displayName)
                else SpeechOutputTarget.Phone
            eventBus.publish(TtsRequested(text = text, transactionId = id, target = target,
                fallback = if (target is SpeechOutputTarget.Wearable) SpeechOutputFallback.DISALLOW_PHONE else SpeechOutputFallback.ALLOW_PHONE,
                sourceCaptureId = interpretation.origin?.captureId))
        }
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
