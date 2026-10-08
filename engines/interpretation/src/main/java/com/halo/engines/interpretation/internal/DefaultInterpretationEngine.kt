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
    private val agentRunStore: com.halo.runtime.cognition.agent.AgentRunStore? = null,
    private val phoneFallbackEnabled: () -> Boolean = { false },
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
    private val fallbackClarificationCoordinator = com.halo.runtime.cognition.clarification.DefaultClarificationCoordinator()
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
            speechRoute = speechRoute,
            observationOrigin = observation.origin,
        )

        var interpretation = result.interpretation.copy(
            origin = origin,
            metadata = result.interpretation.metadata + buildMap {
            put("observationId", observation.id.value)
            put("interactionId", observation.metadata["interactionId"]?.toString() ?: observation.interactionId)
            put("inputSource", observation.origin?.source?.toString() ?: observation.source.name)
            observation.metadata["inputModality"]?.toString()?.let { put("inputModality", it) }
                ?: if (observation.source == Source.MICROPHONE || speechRoute != null) put("inputModality", "VOICE") else Unit
            observation.sourceDeviceId?.let { put("sourceDeviceId", it) }
            observation.metadata["captureId"]?.let { put("captureId", it.toString()) }
            observation.metadata["inputRoute"]?.let { put("inputRoute", it.toString()) }
        })
        val coordinator = clarificationCoordinator ?: fallbackClarificationCoordinator
        val prior = pendingInterpretation
        if (coordinator != null && prior != null) {
            val now = java.time.Instant.now()
            val pending = coordinator.pending.value
            if (pending == null || !now.isBefore(pending.expiresAt)) {
                coordinator.cancel("Expired")
                prior.metadata["agentRunId"]?.let { rid ->
                    val runId = AgentRunId(rid)
                    journalClarificationResolved(runId, "Expired")
                    agentRunCoordinator?.cancel(runId, "Clarification expired")
                }
                pendingInterpretation = null
                clarificationExpiryJob?.cancel()
            } else if (prior.origin?.sourceDeviceId == interpretation.origin?.sourceDeviceId &&
                prior.origin?.source == interpretation.origin?.source && prior.origin?.speechRoute == interpretation.origin?.speechRoute) {
                val reply = interpretation.transcript.trim().lowercase().trimEnd('.', '!', '?')
                if (reply in setOf("cancel", "cancel it", "never mind", "nevermind")) {
                    coordinator.cancel("User cancelled")
                    prior.metadata["agentRunId"]?.let { rid ->
                        val runId = AgentRunId(rid)
                        journalClarificationResolved(runId, "Cancelled")
                        agentRunCoordinator?.cancel(runId, "User cancelled")
                    }
                    pendingInterpretation = null
                    clarificationExpiryJob?.cancel()
                    interpretation = interpretation.copy(metadata = interpretation.metadata + ("clarificationOnly" to "true"))
                    publishClarificationResponse(prior, "Cancelled.")
                } else when (val resolution = coordinator.consume(interpretation.transcript, now)) {
                    is ClarificationResolution.Resolved -> {
                        interpretation = prior.copy(entities = resolution.entities,
                            metadata = prior.metadata - "clarificationOnly")
                        prior.metadata["agentRunId"]?.let { rid ->
                            val runId = AgentRunId(rid)
                            journalClarificationResolved(runId, resolution.entities.lastOrNull()?.let { entity ->
                                (entity as? com.halo.cognition.language.TimeEntity)?.expression
                            } ?: interpretation.transcript.trim())
                            agentRunCoordinator?.transition(runId, AgentRunState.PLANNING)
                        }
                        pendingInterpretation = null
                        clarificationExpiryJob?.cancel()
                    }
                    else -> Unit // Unrelated input continues normally and cannot fill the pending field.
                }
            }
        }
        if (coordinator != null && (interpretation.intent is SetAlarmIntent || interpretation.intent is ReminderIntent) &&
            interpretation.metadata["clarificationOnly"] != "true") {
            var reminderEntity = interpretation.entities.filterIsInstance<ReminderEntity>().firstOrNull()
            val canonicalReminderTime = reminderEntity?.temporalExpression?.let { com.halo.cognition.language.ReminderTemporalValidation.canonical(it) }
            if (canonicalReminderTime != null) {
                interpretation = interpretation.copy(entities = interpretation.entities.map {
                    if (it is ReminderEntity) it.copy(temporalExpression = canonicalReminderTime) else it
                })
                reminderEntity = interpretation.entities.filterIsInstance<ReminderEntity>().firstOrNull()
            }
            val reminderTime = reminderEntity?.temporalExpression
            val hasNoReminderTime = interpretation.intent is ReminderIntent && reminderTime.isNullOrBlank()
            val unresolved = interpretation.entities.filterIsInstance<UnresolvedTimeEntity>().firstOrNull()
                ?: if (interpretation.intent is ReminderIntent && reminderTime != null &&
                    canonicalReminderTime == null)
                    UnresolvedTimeEntity(reminderTime, if (com.halo.cognition.language.ReminderTemporalValidation.ambiguous(reminderTime)) TemporalField.AM_PM else TemporalField.TIME_OF_DAY, 1f) else null
            val needsTime = unresolved != null || hasNoReminderTime || (interpretation.intent is SetAlarmIntent &&
                interpretation.entities.filterIsInstance<TimeEntity>().none { it.value is TemporalValue.TimeOfDay })
            if (needsTime) {
                pendingInterpretation?.metadata?.get("agentRunId")?.let {
                    agentRunCoordinator?.cancel(AgentRunId(it), "Replaced by a new clarification")
                }
                val interactionId = InteractionId(interpretation.metadata.getValue("interactionId"))
                val route = interpretation.origin?.speechRoute
                val target = if (route is SpeechInputRoute.Device) SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind) else SpeechOutputTarget.Phone
                val runId = agentRunCoordinator?.start(AgentRequest(interactionId, interpretation.transcript, observation.origin,
                    speechOutputTarget = target, sourceCaptureId = interpretation.origin?.captureId))
                val now = java.time.Instant.now()
                val expression = unresolved?.originalText.orEmpty()
                val question = if (hasNoReminderTime) "When should I remind you?"
                    else if (unresolved?.missingField == TemporalField.AM_PM) "$expression in the morning or evening?"
                    else "What exact time should I use?"
                runId?.let {
                    /*
                     * RUNTIME-019: clarification state lives in the durable journal
                     * (AgentInputRequest.Clarification), not only in memory. The
                     * in-memory coordinator keeps the temporal interpretation, but
                     * process death now leaves the request pending and recoverable.
                     */
                    agentRunCoordinator?.transition(it, AgentRunState.PLANNING)
                    val durableRequest = AgentInputRequest.Clarification(
                        id = AgentInputRequestId(UUID.randomUUID().toString()),
                        runId = it,
                        createdAt = now,
                        expiresAt = now.plusSeconds(120),
                        question = question,
                        field = "time",
                        expected = com.halo.runtime.cognition.agent.ExpectedInput.Text(singleLine = true),
                    )
                    agentRunCoordinator?.recordStep(
                        AgentStep.InputRequested(
                            AgentStepId(UUID.randomUUID().toString()), it, now,
                            durableRequest.id, durableRequest,
                        )
                    )
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
                            runId?.let { rid ->
                                journalClarificationResolved(rid, "Expired")
                                agentRunCoordinator?.cancel(rid, "Clarification expired")
                            }
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

    /**
     * RUNTIME-019: journal the clarification answer/cancellation/expiry so the
     * durable request is never left pending without evidence. Best-effort: a
     * journaling failure must not break interpretation flow.
     */
    private suspend fun journalClarificationResolved(runId: AgentRunId, resolution: String) {
        runCatching {
            val run = agentRunStore?.get(runId) ?: return
            val requested = run.steps.filterIsInstance<AgentStep.InputRequested>()
                .lastOrNull { it.input is AgentInputRequest.Clarification } ?: return
            val resolvedAlready = run.steps.filterIsInstance<AgentStep.InputResolved>()
                .any { it.requestId == requested.requestId }
            if (resolvedAlready) return
            agentRunCoordinator?.recordStep(
                AgentStep.InputResolved(
                    AgentStepId(UUID.randomUUID().toString()), runId,
                    java.time.Instant.now(), requested.requestId, resolution,
                )
            )
        }
    }

    private suspend fun publishClarificationResponse(
        interpretation: com.halo.runtime.cognition.blackboard.Interpretation, text: String,
    ) {
        val id = interpretation.metadata.getValue("interactionId")
        val delivery = interpretation.source.defaultResponseDelivery()
        eventBus.publish(AssistantResponsePublished(text = text, delivery = delivery, transactionId = id))
        if (delivery == ResponseDelivery.SPEECH_AND_VISUAL) {
            val route = interpretation.origin?.speechRoute
            val target = if (route is SpeechInputRoute.Device) SpeechOutputTarget.Device(route.deviceId, route.platformEndpointId, route.displayName, route.kind)
                else SpeechOutputTarget.Phone
            eventBus.publish(TtsRequested(text = text, transactionId = id, target = target,
                fallback = com.halo.sdk.runtime.fallbackFor(route, phoneFallbackEnabled()),
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
