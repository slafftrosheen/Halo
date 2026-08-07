package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class TimelineRecorder(
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private val _timelineFlow = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineFlow: StateFlow<List<TimelineEvent>> = _timelineFlow.asStateFlow()

    init {
        scope.launch {
            eventBus.events().collect { event ->
                val newEvent = mapEvent(event)
                if (newEvent != null) {
                    val currentList = _timelineFlow.value.toMutableList()
                    currentList.add(newEvent)
                    if (currentList.size > 50) {
                        currentList.removeAt(0)
                    }
                    _timelineFlow.value = currentList
                }
            }
        }
    }

    private fun mapEvent(event: HaloEvent): TimelineEvent? {
        val timestamp = Instant.ofEpochMilli(event.timestamp)
        return when (event) {
            is SpeechRecognized -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Speech Recognized",
                description = event.text,
                timestamp = timestamp,
                status = "Success"
            )
            is IntentRecognized -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Intent Recognized",
                description = event.intent.javaClass.simpleName,
                timestamp = timestamp,
                status = "Success"
            )
            is AIResponseGenerated -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "AI Response",
                description = event.response,
                timestamp = timestamp,
                status = "Success"
            )
            is EngineStarted -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Engine Started",
                description = event.engineId,
                timestamp = timestamp,
                status = "Started"
            )
            is EngineFailed -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Engine Failed",
                description = "${event.engineId}: ${event.reason}",
                timestamp = timestamp,
                status = "Failed"
            )
            is GoalCreated -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Goal Created",
                description = event.goalId,
                timestamp = timestamp,
                status = "Created"
            )
            is PlanGenerated -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Plan Generated",
                description = event.planId,
                timestamp = timestamp,
                status = "Generated"
            )
            is ExecutionStarted -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Execution Started",
                description = event.planId,
                timestamp = timestamp,
                status = "Started"
            )
            is ExecutionFinished -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Execution Finished",
                description = event.planId,
                timestamp = timestamp,
                status = "Finished"
            )
            is OutcomePublished -> TimelineEvent(
                id = UUID.randomUUID().toString(),
                title = "Outcome Published",
                description = event.outcome,
                timestamp = timestamp,
                status = "Published"
            )
            else -> null
        }
    }
}
