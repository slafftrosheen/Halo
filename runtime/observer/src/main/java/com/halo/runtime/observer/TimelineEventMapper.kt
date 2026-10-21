package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import java.time.Instant

interface TimelineEventMapper {
    fun map(event: HaloEvent): TimelineEvent?
}

fun timelinePresentationId(
    type: String,
    correlationId: String?,
    timestamp: Instant,
    discriminator: String,
): String = buildString {
    append(type)
    append(':')
    append(correlationId ?: "none")
    append(':')
    append(timestamp.toEpochMilli())
    append(':')
    append(discriminator)
}

class DefaultTimelineEventMapper : TimelineEventMapper {
    private val interactions = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1000
    }
    @Synchronized
    override fun map(event: HaloEvent): TimelineEvent? {
        if (event is CognitiveRequestStarted) {
            val iId = event.interactionId
            if (iId != null) interactions[event.requestId] = iId
        }
        val timestamp = Instant.ofEpochMilli(event.timestamp)
        val correlationId = when (event) {
            is ActionReceipt -> event.operationId
            is ToolCallRequested -> interactions[event.requestId] ?: event.requestId
            is ToolCallAuthorized -> interactions[event.requestId] ?: event.requestId
            is ToolCallRejected -> interactions[event.requestId] ?: event.requestId
            is ToolCallCompleted -> interactions[event.requestId] ?: event.requestId
            is AssistantResponsePublished -> interactions[event.transactionId] ?: event.transactionId
            is ObservationCreated -> event.observation.interactionId
            is IntentRecognized -> event.id
            is AIResponseGenerated -> event.id
            is EngineStarted -> event.engineId
            is EngineFailed -> event.engineId
            is GoalCreated -> event.goalId
            is PlanGenerated -> event.planId
            is ExecutionStarted -> event.planId
            is ExecutionFinished -> event.planId
            is OutcomePublished -> event.id
            is CognitiveRequestStarted -> interactions[event.requestId] ?: event.requestId
            is CognitiveReasoningStarted -> interactions[event.requestId] ?: event.requestId
            is CognitiveCompleted -> interactions[event.requestId] ?: event.requestId
            is CognitiveFailed -> interactions[event.requestId] ?: event.requestId
            is StageMetricsPublished -> event.observationId
            is ObservationReceived -> event.observationId
            is InterpretationProduced -> event.observationId
            is IntentCreated -> event.intentId
            is IntentResolved -> event.intentId
            is IntentDispatched -> event.intentId
            is ProviderRequest -> interactions[event.requestId] ?: event.requestId
            is ProviderResult -> interactions[event.requestId] ?: event.requestId
            is ProviderFailure -> interactions[event.requestId] ?: event.requestId
            is EmbodimentExecutionEvent -> event.result.intentId
            else -> event.id
        }
        return when (event) {
            is ActionReceipt -> TimelineEvent(
                presentationId = timelinePresentationId("ActionReceipt", correlationId, timestamp, "receipt"),
                correlationId = correlationId,
                title = event.title, description = event.detail, timestamp = timestamp, status = event.stage,
                intentId = event.operationId, category = event.category, objectId = event.objectId,
                details = mapOf("operation" to event.operationId, "source" to (event.sourceDeviceId?.takeLast(5) ?: "Phone")))
            is ToolCallRequested -> TimelineEvent(
                presentationId = timelinePresentationId("ToolCall", correlationId, timestamp, "requested"),
                correlationId = correlationId,
                title = event.toolId, description = "Model requested a tool", timestamp = timestamp, status = "Requested", intentId = event.toolCallId, category = "Actions")
            is ToolCallAuthorized -> TimelineEvent(
                presentationId = timelinePresentationId("ToolCall", correlationId, timestamp, "authorized"),
                correlationId = correlationId,
                title = event.toolId, description = "Tool authorized", timestamp = timestamp, status = "Authorized", intentId = event.toolCallId, category = "Actions")
            is ToolCallRejected -> TimelineEvent(
                presentationId = timelinePresentationId("ToolCall", correlationId, timestamp, "rejected"),
                correlationId = correlationId,
                title = event.toolId, description = event.reason, timestamp = timestamp, status = "Rejected", intentId = event.toolCallId, category = "Actions")
            is ToolCallCompleted -> TimelineEvent(
                presentationId = timelinePresentationId("ToolCall", correlationId, timestamp, "completed"),
                correlationId = correlationId,
                title = event.toolId, description = "Tool executor returned; see action receipt for dispatch/completion", timestamp = timestamp,
                status = if (event.success) "Returned" else "Failed", intentId = event.toolCallId, category = "Actions",
                details = event.actionTruth?.let { mapOf("actionTruth" to it) }.orEmpty())
            is AssistantResponsePublished -> TimelineEvent(
                presentationId = timelinePresentationId("Assistant", correlationId, timestamp, "published"),
                correlationId = correlationId,
                title = "Halo",
                description = event.text,
                timestamp = timestamp,
                status = "Completed"
            )
            is ObservationCreated -> {
                val input = event.observation.payload as? SpeechObservationPayload
                val userInput = event.observation.source == Source.USER ||
                    event.observation.source == Source.MICROPHONE
                if (input != null && userInput && input.transcript.isNotBlank()) {
                    TimelineEvent(
                        presentationId = timelinePresentationId("Observation", correlationId, timestamp, "created"),
                        correlationId = correlationId,
                        title = "You",
                        description = input.transcript.trim(),
                        timestamp = Instant.ofEpochMilli(event.observation.timestamp),
                        status = if (event.observation.source == Source.USER) "Sent" else "Received",
                        category = if (event.observation.modality == Modality.AUDIO) "Voice" else "All",
                        details = buildMap {
                            (event.observation.metadata["recognitionQuality"] as? RecognitionQuality)?.let { quality ->
                                putAll(speechDetails(quality, event.observation.metadata["recognitionMetrics"] as? com.halo.sdk.runtime.SpeechRecognitionMetrics))
                            }
                            put("gate", "Accepted")
                            put("source", event.observation.origin?.source?.toString() ?: event.observation.source.name)
                            event.observation.sourceDeviceId?.let { put("device", it.takeLast(5)) }
                            event.observation.metadata["captureId"]?.let { put("capture", it.toString()) }
                        }
                    )
                } else {
                    null
                }
            }
            // SpeechRecognized is an internal speech-stage event. The canonical
            // ObservationCreated event above records the user turn once for both
            // microphone and keyboard input, avoiding duplicate Activity entries.
            is SpeechRecognized -> null
            is SpeechRejected -> TimelineEvent(
                presentationId = timelinePresentationId("Speech", event.captureId, timestamp, "rejected"),
                correlationId = event.captureId, title = "Speech rejected",
                description = event.reason + "\n" + event.text,
                timestamp = timestamp, status = "Rejected", category = "Voice",
                details = speechDetails(event.quality, event.metrics) + mapOf("gate" to event.reason, "source" to (event.input?.source?.toString() ?: "unknown"))
            )
            is IntentRecognized -> TimelineEvent(
                presentationId = timelinePresentationId("Intent", correlationId, timestamp, "recognized"),
                correlationId = correlationId,
                title = "Intent Recognized",
                description = event.intent.javaClass.simpleName,
                timestamp = timestamp,
                status = "Success"
            )
            is AIResponseGenerated -> TimelineEvent(
                presentationId = timelinePresentationId("AI", correlationId, timestamp, "generated"),
                correlationId = correlationId,
                title = "AI Response",
                description = event.response,
                timestamp = timestamp,
                status = "Success"
            )
            is EngineStarted -> TimelineEvent(
                presentationId = timelinePresentationId("Engine", correlationId, timestamp, "started"),
                correlationId = correlationId,
                title = "Engine Started",
                description = event.engineId,
                timestamp = timestamp,
                status = "Started"
            )
            is EngineFailed -> TimelineEvent(
                presentationId = timelinePresentationId("Engine", correlationId, timestamp, "failed"),
                correlationId = correlationId,
                title = "Engine Failed",
                description = "${event.engineId}: ${event.reason}",
                timestamp = timestamp,
                status = "Failed"
            )
            is GoalCreated -> TimelineEvent(
                presentationId = timelinePresentationId("Goal", correlationId, timestamp, "created"),
                correlationId = correlationId,
                title = "Goal Created",
                description = event.goalId,
                timestamp = timestamp,
                status = "Created"
            )
            is PlanGenerated -> TimelineEvent(
                presentationId = timelinePresentationId("Plan", correlationId, timestamp, "generated"),
                correlationId = correlationId,
                title = "Plan Generated",
                description = event.planId,
                timestamp = timestamp,
                status = "Generated"
            )
            is ExecutionStarted -> TimelineEvent(
                presentationId = timelinePresentationId("Execution", correlationId, timestamp, "started"),
                correlationId = correlationId,
                title = "Execution Started",
                description = event.planId,
                timestamp = timestamp,
                status = "Started"
            )
            is ExecutionFinished -> TimelineEvent(
                presentationId = timelinePresentationId("Execution", correlationId, timestamp, "finished"),
                correlationId = correlationId,
                title = "Execution Finished",
                description = event.planId,
                timestamp = timestamp,
                status = "Finished"
            )
            is OutcomePublished -> TimelineEvent(
                presentationId = timelinePresentationId("Outcome", correlationId, timestamp, "published"),
                correlationId = correlationId,
                title = "Outcome Published",
                description = event.outcome,
                timestamp = timestamp,
                status = "Published"
            )
            is CognitiveRequestStarted -> TimelineEvent(
                presentationId = timelinePresentationId("Cognitive", correlationId, timestamp, "started"),
                correlationId = correlationId,
                title = "Cognitive Request",
                description = "Started",
                timestamp = timestamp,
                status = "Started"
            )
            is CognitiveReasoningStarted -> TimelineEvent(
                presentationId = timelinePresentationId("Reasoning", correlationId, timestamp, "started"),
                correlationId = correlationId,
                title = "Reasoning",
                description = "Provider: ${event.providerId}",
                timestamp = timestamp,
                status = "Started"
            )
            is CognitiveCompleted -> TimelineEvent(
                presentationId = timelinePresentationId("Cognitive", correlationId, timestamp, "completed"),
                correlationId = correlationId,
                title = "Cognitive Response",
                description = event.response ?: "Completed",
                timestamp = timestamp,
                status = "Success"
            )
            is CognitiveFailed -> TimelineEvent(
                presentationId = timelinePresentationId("Cognitive", correlationId, timestamp, "failed"),
                correlationId = correlationId,
                title = "Halo",
                description = event.message,
                timestamp = timestamp,
                status = "Failed"
            )
            is StageMetricsPublished -> TimelineEvent(
                presentationId = timelinePresentationId("Metrics", correlationId, timestamp, "published"),
                correlationId = correlationId,
                title = event.stage.name,
                description = "${event.latency} ms\n" + if (event.success) "✓" else "✗",
                timestamp = timestamp,
                status = if (event.success) "Success" else "Failed"
            )
            is ObservationReceived -> TimelineEvent(
                presentationId = timelinePresentationId("Observation", correlationId, timestamp, "received"),
                correlationId = correlationId,
                title = "Observation",
                description = "${event.source}: ${event.content}",
                timestamp = timestamp,
                status = "Received"
            )
            is InterpretationProduced -> TimelineEvent(
                presentationId = timelinePresentationId("Interpretation", correlationId, timestamp, "produced"),
                correlationId = correlationId,
                title = "Interpretation",
                description = event.interpretation,
                timestamp = timestamp,
                status = "Produced"
            )
            is IntentCreated -> TimelineEvent(
                presentationId = timelinePresentationId("Intent", correlationId, timestamp, "created"),
                correlationId = correlationId,
                title = "Intent Created",
                description = "${event.intentKind} [${event.intentId}]",
                timestamp = timestamp,
                status = "Created",
                intentId = event.intentId
            )
            is IntentResolved -> TimelineEvent(
                presentationId = timelinePresentationId("Intent", correlationId, timestamp, "resolved"),
                correlationId = correlationId,
                title = "Intent Resolved",
                description = "Device: ${event.deviceId}",
                timestamp = timestamp,
                status = "Resolved",
                intentId = event.intentId
            )
            is IntentDispatched -> TimelineEvent(
                presentationId = timelinePresentationId("Intent", correlationId, timestamp, "dispatched"),
                correlationId = correlationId,
                title = "Intent Dispatched",
                description = "To: ${event.deviceId}",
                timestamp = timestamp,
                status = "Dispatched",
                intentId = event.intentId
            )
            is DeviceConnected -> TimelineEvent(
                presentationId = timelinePresentationId("Device", correlationId, timestamp, "connected"),
                correlationId = correlationId,
                title = "Device Connected",
                description = "${event.deviceId} (${event.sessionId})",
                timestamp = timestamp,
                status = "Connected"
            )
            is DeviceReady -> TimelineEvent(
                presentationId = timelinePresentationId("Device", correlationId, timestamp, "ready"),
                correlationId = correlationId,
                title = "Device Ready",
                description = event.deviceId,
                timestamp = timestamp,
                status = "Ready"
            )
            is DeviceDisconnected -> TimelineEvent(
                presentationId = timelinePresentationId("Device", correlationId, timestamp, "disconnected"),
                correlationId = correlationId,
                title = "Device Disconnected",
                description = event.deviceId,
                timestamp = timestamp,
                status = "Disconnected"
            )
            is CapabilityUpdated -> TimelineEvent(
                presentationId = timelinePresentationId("Capability", correlationId, timestamp, "updated"),
                correlationId = correlationId,
                title = "Capabilities Updated",
                description = "${event.deviceId}: ${event.capabilities.size} capabilities",
                timestamp = timestamp,
                status = "Updated"
            )
            is RuntimeStateChanged -> TimelineEvent(
                presentationId = timelinePresentationId("State", correlationId, timestamp, "changed"),
                correlationId = correlationId,
                title = "State Changed",
                description = "${event.deviceId} -> ${event.state}",
                timestamp = timestamp,
                status = "Changed"
            )
            is ProviderRequest -> TimelineEvent(
                presentationId = timelinePresentationId("Provider", correlationId, timestamp, "request"),
                correlationId = correlationId,
                title = "Provider Request",
                description = "${event.providerId} [${event.requestId}]",
                timestamp = timestamp,
                status = "Started"
            )
            is ProviderResult -> TimelineEvent(
                presentationId = timelinePresentationId("Provider", correlationId, timestamp, "result"),
                correlationId = correlationId,
                title = "Provider Result",
                description = if (event.success) "Success" else "Failed: ${event.reason}",
                timestamp = timestamp,
                status = if (event.success) "Success" else "Failed"
            )
            is ProviderFailure -> TimelineEvent(
                presentationId = timelinePresentationId("Provider", correlationId, timestamp, "failure"),
                correlationId = correlationId,
                title = "Provider Error",
                description = "${event.providerId}: ${event.reason}",
                timestamp = timestamp,
                status = "Failed"
            )
            is EmbodimentExecutionEvent -> TimelineEvent(
                presentationId = timelinePresentationId("Embodiment", correlationId, timestamp, "execution"),
                correlationId = correlationId,
                title = "Execution Result",
                description = "${event.device.displayName}: ${event.result.reason ?: event.result.status}",
                timestamp = timestamp,
                status = when (event.result.status) {
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.SUCCEEDED -> "Completed"
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.ACCEPTED -> "Acknowledged"
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.ABORTED -> "Cancelled"
                    else -> event.result.status.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                category = "Actions",
                intentId = event.result.intentId
            )
            else -> null
        }
    }
}

private fun speechDetails(quality: RecognitionQuality, metrics: com.halo.sdk.runtime.SpeechRecognitionMetrics?): Map<String, String> = buildMap {
    metrics?.modelDisplayName?.let { model ->
        put("stt", listOfNotNull(model, metrics?.language,
            metrics?.decodeMs?.let { "$it ms" }, metrics?.realTimeFactor?.let { "RTF %.2f".format(java.util.Locale.ROOT, it) }).joinToString(" · "))
    }
    metrics?.providerId?.let { put("provider", it) }
    metrics?.modelId?.let { put("model", it) }
    metrics?.language?.let { put("language", it) }
    metrics?.decodeMs?.let { put("decodeMs", it.toString()) }
    metrics?.realTimeFactor?.let { put("RTF", it.toString()) }
    quality.voicedDurationMs?.let { put("voicedMs", it.toString()) }
    quality.segmentCount?.let { put("segments", it.toString()) }
    quality.longestSegmentMs?.let { put("longestSegmentMs", it.toString()) }
    quality.audioDurationMs?.let { put("audioMs", it.toString()) }
    metrics?.loadMs?.let { put("loadMs", it.toString()) }
    metrics?.totalMs?.let { put("totalMs", it.toString()) }
    quality.voicedRatio?.let { put("voicedRatio", it.toString()) }
    quality.peakAmplitude?.let { put("peak", it.toString()) }
    quality.qualityEstimator?.let { put("estimator", if (it == "SIMPLE_ENERGY") "Energy fallback" else it) }
}
