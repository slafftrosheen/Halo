package com.halo.runtime.cognition.blackboard

import com.halo.runtime.cognition.Decision
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Source

import com.halo.cognition.language.Entity
import com.halo.cognition.language.Intent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.InterpretationStrategy

data class Interpretation(
    val transcript: String = "",
    val language: String = "und",
    val intent: Intent = ConversationIntent,
    val entities: List<Entity> = emptyList(),
    val confidence: Float = 0.0f,
    val source: Source = Source.USER,
    val metadata: Map<String, String> = emptyMap(),
    val strategy: InterpretationStrategy = InterpretationStrategy.Semantic
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

data class GoalKnowledge(
    val goal: com.halo.icl.ontology.Goal
) : KnowledgePayload

data class TaskPayload(
    val task: Task
) : KnowledgePayload

data class DecisionPayload(
    val decision: Decision
) : KnowledgePayload
