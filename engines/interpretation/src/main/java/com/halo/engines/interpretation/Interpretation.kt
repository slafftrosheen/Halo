package com.halo.engines.interpretation

import com.halo.sdk.runtime.HaloEvent
import com.halo.runtime.cognition.blackboard.KnowledgeId
import java.util.UUID

typealias Interpretation = com.halo.engines.interpretation.model.Interpretation

data class InterpretationStarted(
    override val id: String = UUID.randomUUID().toString(),
    val transcript: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RuleMatched(
    override val id: String = UUID.randomUUID().toString(),
    val ruleName: String,
    val confidence: Float,
    val entityCount: Int,
    val diagnostics: Map<String, String> = emptyMap(),
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RuleExecution(
    override val id: String = UUID.randomUUID().toString(),
    val rule: String,
    val latencyMillis: Long,
    val matched: Boolean,
    val confidence: Float,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationBuilt(
    override val id: String = UUID.randomUUID().toString(),
    val interpretation: Interpretation,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationCreated(
    override val id: String = UUID.randomUUID().toString(),
    val interpretation: Interpretation,
    val knowledgeId: KnowledgeId? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationPublished(
    override val id: String = UUID.randomUUID().toString(),
    val knowledgeId: KnowledgeId,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
