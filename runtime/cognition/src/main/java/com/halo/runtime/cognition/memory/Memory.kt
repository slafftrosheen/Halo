package com.halo.runtime.cognition.memory

import java.util.UUID
import kotlin.time.Duration
import kotlinx.datetime.Instant

import com.halo.sdk.runtime.Memory as SdkMemory

sealed interface Memory : SdkMemory {
    override val id: UUID
    val importance: Float
    val recency: Instant
    val confidence: Float
    val frequency: Int
    val ttl: Duration?
}

data class FactMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val subject: String,
    val predicate: String,
    val obj: String
) : Memory

data class EpisodeMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val goalId: UUID,
    val executionResult: String,
    val reflectionNotes: String
) : Memory

/** Historical capability-use reference; durable recipes belong to LearnedProcedureStore. */
data class SkillMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val action: String,
    val capabilityId: String
) : Memory

data class PreferenceMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val category: String,
    val preference: String
) : Memory

data class RelationshipMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val sourceNodeId: String,
    val targetNodeId: String,
    val relationType: String
) : Memory

/** Transient Runtime state only. Never map to a durable personal MemoryRecord. */
data class WorldStateMemory(
    override val id: UUID = UUID.randomUUID(),
    override val importance: Float,
    override val recency: Instant,
    override val confidence: Float,
    override val frequency: Int,
    override val ttl: Duration? = null,
    val stateKey: String,
    val stateValue: String
) : Memory


// Episode and Reflection are defined in PersonalMemory.kt. Legacy value types above are portable only.

