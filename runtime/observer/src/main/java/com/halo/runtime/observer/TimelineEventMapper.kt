package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import java.time.Instant

interface TimelineEventMapper {
    fun map(event: HaloEvent): TimelineEvent?
}

class DefaultTimelineEventMapper : TimelineEventMapper {
    override fun map(event: HaloEvent): TimelineEvent? {
        val timestamp = Instant.ofEpochMilli(event.timestamp)
        return when (event) {
            is ActionReceipt -> TimelineEvent(event.id, event.title, event.detail, timestamp, event.stage,
                intentId = event.operationId, category = event.category, objectId = event.objectId,
                details = mapOf("operation" to event.operationId, "source" to (event.sourceDeviceId?.takeLast(5) ?: "Phone")))
            is ToolCallRequested -> TimelineEvent(event.id, event.toolId, "Model requested a tool", timestamp, "Requested", event.toolCallId, "Actions")
            is ToolCallAuthorized -> TimelineEvent(event.id, event.toolId, "Tool authorized", timestamp, "Authorized", event.toolCallId, "Actions")
            is ToolCallRejected -> TimelineEvent(event.id, event.toolId, event.reason, timestamp, "Rejected", event.toolCallId, "Actions")
            is ToolCallCompleted -> TimelineEvent(event.id, event.toolId, "Tool executor returned; see action receipt for dispatch/completion", timestamp,
                if (event.success) "Returned" else "Failed", event.toolCallId, "Actions")
            is AssistantResponsePublished -> TimelineEvent(
                id = event.id,
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
                        id = event.id,
                        title = "You",
                        description = input.transcript.trim(),
                        timestamp = Instant.ofEpochMilli(event.observation.timestamp),
                        status = if (event.observation.source == Source.USER) "Sent" else "Received",
                        category = if (event.observation.modality == Modality.AUDIO) "Voice" else "All",
                        details = buildMap {
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
            is IntentRecognized -> TimelineEvent(
                id = event.id,
                title = "Intent Recognized",
                description = event.intent.javaClass.simpleName,
                timestamp = timestamp,
                status = "Success"
            )
            is AIResponseGenerated -> TimelineEvent(
                id = event.id,
                title = "AI Response",
                description = event.response,
                timestamp = timestamp,
                status = "Success"
            )
            is EngineStarted -> TimelineEvent(
                id = event.id,
                title = "Engine Started",
                description = event.engineId,
                timestamp = timestamp,
                status = "Started"
            )
            is EngineFailed -> TimelineEvent(
                id = event.id,
                title = "Engine Failed",
                description = "${event.engineId}: ${event.reason}",
                timestamp = timestamp,
                status = "Failed"
            )
            is GoalCreated -> TimelineEvent(
                id = event.id,
                title = "Goal Created",
                description = event.goalId,
                timestamp = timestamp,
                status = "Created"
            )
            is PlanGenerated -> TimelineEvent(
                id = event.id,
                title = "Plan Generated",
                description = event.planId,
                timestamp = timestamp,
                status = "Generated"
            )
            is ExecutionStarted -> TimelineEvent(
                id = event.id,
                title = "Execution Started",
                description = event.planId,
                timestamp = timestamp,
                status = "Started"
            )
            is ExecutionFinished -> TimelineEvent(
                id = event.id,
                title = "Execution Finished",
                description = event.planId,
                timestamp = timestamp,
                status = "Finished"
            )
            is OutcomePublished -> TimelineEvent(
                id = event.id,
                title = "Outcome Published",
                description = event.outcome,
                timestamp = timestamp,
                status = "Published"
            )
            is CognitiveRequestStarted -> TimelineEvent(
                id = event.id,
                title = "Cognitive Request",
                description = "Started",
                timestamp = timestamp,
                status = "Started"
            )
            is CognitiveReasoningStarted -> TimelineEvent(
                id = event.id,
                title = "Reasoning",
                description = "Provider: ${event.providerId}",
                timestamp = timestamp,
                status = "Started"
            )
            is CognitiveCompleted -> TimelineEvent(
                id = event.id,
                title = "Cognitive Response",
                description = event.response ?: "Completed",
                timestamp = timestamp,
                status = "Success"
            )
            is CognitiveFailed -> TimelineEvent(
                id = event.id,
                title = "Halo",
                description = event.message,
                timestamp = timestamp,
                status = "Failed"
            )
            is StageMetricsPublished -> TimelineEvent(
                id = event.id,
                title = event.stage.name,
                description = "${event.latency} ms\n" + if (event.success) "✓" else "✗",
                timestamp = timestamp,
                status = if (event.success) "Success" else "Failed"
            )
            is ObservationReceived -> TimelineEvent(
                id = event.id,
                title = "Observation",
                description = "${event.source}: ${event.content}",
                timestamp = timestamp,
                status = "Received"
            )
            is InterpretationProduced -> TimelineEvent(
                id = event.id,
                title = "Interpretation",
                description = event.interpretation,
                timestamp = timestamp,
                status = "Produced"
            )
            is IntentCreated -> TimelineEvent(
                id = event.id,
                title = "Intent Created",
                description = "${event.intentKind} [${event.intentId}]",
                timestamp = timestamp,
                status = "Created",
                intentId = event.intentId
            )
            is IntentResolved -> TimelineEvent(
                id = event.id,
                title = "Intent Resolved",
                description = "Device: ${event.deviceId}",
                timestamp = timestamp,
                status = "Resolved",
                intentId = event.intentId
            )
            is IntentDispatched -> TimelineEvent(
                id = event.id,
                title = "Intent Dispatched",
                description = "To: ${event.deviceId}",
                timestamp = timestamp,
                status = "Dispatched",
                intentId = event.intentId
            )
            is DeviceConnected -> TimelineEvent(
                id = event.id,
                title = "Device Connected",
                description = "${event.deviceId} (${event.sessionId})",
                timestamp = timestamp,
                status = "Connected"
            )
            is DeviceReady -> TimelineEvent(
                id = event.id,
                title = "Device Ready",
                description = event.deviceId,
                timestamp = timestamp,
                status = "Ready"
            )
            is DeviceDisconnected -> TimelineEvent(
                id = event.id,
                title = "Device Disconnected",
                description = event.deviceId,
                timestamp = timestamp,
                status = "Disconnected"
            )
            is CapabilityUpdated -> TimelineEvent(
                id = event.id,
                title = "Capabilities Updated",
                description = "${event.deviceId}: ${event.capabilities.size} capabilities",
                timestamp = timestamp,
                status = "Updated"
            )
            is RuntimeStateChanged -> TimelineEvent(
                id = event.id,
                title = "State Changed",
                description = "${event.deviceId} -> ${event.state}",
                timestamp = timestamp,
                status = "Changed"
            )
            is ProviderRequest -> TimelineEvent(
                id = event.id,
                title = "Provider Request",
                description = "${event.providerId} [${event.requestId}]",
                timestamp = timestamp,
                status = "Started"
            )
            is ProviderResult -> TimelineEvent(
                id = event.id,
                title = "Provider Result",
                description = if (event.success) "Success" else "Failed: ${event.reason}",
                timestamp = timestamp,
                status = if (event.success) "Success" else "Failed"
            )
            is ProviderFailure -> TimelineEvent(
                id = event.id,
                title = "Provider Error",
                description = "${event.providerId}: ${event.reason}",
                timestamp = timestamp,
                status = "Failed"
            )
            is EmbodimentExecutionEvent -> TimelineEvent(
                id = event.id,
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
