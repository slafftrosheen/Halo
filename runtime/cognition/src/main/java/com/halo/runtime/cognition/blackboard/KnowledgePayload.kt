package com.halo.runtime.cognition.blackboard

import com.halo.runtime.cognition.Decision
import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation

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

data class TimeEntity(
    val expression: String,
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

data class IntentCandidate(
    val name: String,
    val confidence: Float
)

data class Slot(
    val key: String,
    val value: Any
)

data class Interpretation(
    val entities: List<Entity> = emptyList(),
    val candidates: List<IntentCandidate> = emptyList(),
    val slots: List<Slot> = emptyList(),
    val confidence: Confidence = Confidence.None
)

sealed interface KnowledgePayload

data class ObservationPayload(
    val observation: Observation
) : KnowledgePayload

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
