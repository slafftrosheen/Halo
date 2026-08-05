package com.halo.runtime.cognition.blackboard

import com.halo.runtime.cognition.Decision
import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Source

sealed interface Entity {
    val confidence: Float
}

data class PersonEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class LocationEntity(
    val location: String,
    override val confidence: Float
) : Entity

data class PlaceEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class TimeEntity(
    val expression: String,
    override val confidence: Float
) : Entity

data class DateEntity(
    val expression: String,
    override val confidence: Float
) : Entity

data class NumberEntity(
    val value: Double,
    override val confidence: Float
) : Entity

data class ObjectEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class ApplicationEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class DeviceEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class MediaEntity(
    val title: String,
    override val confidence: Float
) : Entity

data class WebEntity(
    val query: String,
    override val confidence: Float
) : Entity

sealed interface Intent

data object OpenIntent : Intent
data object CallIntent : Intent
data object NavigateIntent : Intent
data object PlayIntent : Intent
data object PauseIntent : Intent
data object ReminderIntent : Intent
data object SearchIntent : Intent
data object QuestionIntent : Intent
data object ConversationIntent : Intent

@Deprecated("Use typed Intent values")
data class IntentCandidate(
    val name: String,
    val confidence: Float
)

@Deprecated("Use typed entities and intent models")
data class Slot(
    val key: String,
    val value: Any
)

data class Interpretation(
    val transcript: String = "",
    val language: String = "und",
    val intent: Intent = ConversationIntent,
    val entities: List<Entity> = emptyList(),
    val confidence: Confidence = Confidence.None,
    val source: Source = Source.USER,
    val metadata: Map<String, String> = emptyMap()
)

interface KnowledgePayload

data class SpeechKnowledge(
    val transcript: String,
    val confidence: Float,
    val language: String,
    val timestamp: Long
) : KnowledgePayload

data class IntentKnowledge(
    val intent: Intent,
    val confidence: Float
) : KnowledgePayload

data class EntityKnowledge(
    val entities: List<Entity>
) : KnowledgePayload

data class InterpretationKnowledge(
    val interpretation: Interpretation
) : KnowledgePayload

data class ObservationPayload(
    val observation: Observation
) : KnowledgePayload

@Deprecated("Use InterpretationKnowledge")
data class InterpretationPayload(
    val interpretation: Interpretation
) : KnowledgePayload

data class GoalPayload(
    val goal: Goal
) : KnowledgePayload

data class TaskPayload(
    val task: Task
) : KnowledgePayload

data class DecisionPayload(
    val decision: Decision
) : KnowledgePayload
