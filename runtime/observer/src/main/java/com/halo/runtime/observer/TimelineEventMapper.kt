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
            is SpeechRecognized -> TimelineEvent(
                id = event.id,
                title = "Speech Recognized",
                description = event.text,
                timestamp = timestamp,
                status = "Success"
            )
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
                title = "Cognitive Failed",
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
            else -> null
        }
    }
}
