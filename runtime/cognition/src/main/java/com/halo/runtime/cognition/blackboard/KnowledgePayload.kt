package com.halo.runtime.cognition.blackboard

import com.halo.runtime.cognition.Decision
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Source

import com.halo.cognition.language.Entity
import com.halo.cognition.language.Intent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.InterpretationStrategy
import com.halo.sdk.runtime.SpeechInputRoute

data class InteractionOrigin(
    val source: Source,
    val sourceDeviceId: String? = null,
    val captureId: String? = null,
    val speechRoute: SpeechInputRoute? = null,
    val observationOrigin: com.halo.sdk.runtime.ObservationOrigin? = null,
)

data class Interpretation(
    val transcript: String = "",
    val language: String = "und",
    val intent: Intent = ConversationIntent,
    val entities: List<Entity> = emptyList(),
    val confidence: Float = 0.0f,
    val source: Source = Source.USER,
    val metadata: Map<String, String> = emptyMap(),
    val strategy: InterpretationStrategy = InterpretationStrategy.Semantic,
    val origin: InteractionOrigin? = null
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

data class ObservationKnowledge(
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

data class PlanKnowledge(
    val plan: com.halo.runtime.cognition.ExecutionPlan
) : KnowledgePayload

data class DecisionPayload(
    val decision: Decision
) : KnowledgePayload

/** The one mapping shared by workload planning and durable run evidence. */
fun InteractionOrigin.toObservationOrigin(): com.halo.sdk.runtime.ObservationOrigin {
    observationOrigin?.let { return it }
    val observationSource = when {
        (speechRoute as? SpeechInputRoute.Device)?.kind == com.halo.sdk.runtime.AudioDeviceKind.WEARABLE -> com.halo.sdk.host.ObservationSource.HeyCyanGlasses
        source == Source.MICROPHONE -> com.halo.sdk.host.ObservationSource.AndroidPhoneMic
        else -> com.halo.sdk.host.ObservationSource.AndroidAssist
    }
    return com.halo.sdk.runtime.ObservationOrigin(observationSource, sourceDeviceId)
}
