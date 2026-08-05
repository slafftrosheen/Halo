package com.halo.engines.interpretation

import com.halo.sdk.runtime.HaloEvent
import com.halo.runtime.cognition.blackboard.KnowledgeId

typealias Interpretation = com.halo.engines.interpretation.model.Interpretation

data class InterpretationStarted(
    val transcript: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class RuleMatched(
    val ruleName: String,
    val confidence: Float,
    val entityCount: Int,
    val diagnostics: String?,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationBuilt(
    val interpretation: Interpretation,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationCreated(
    val interpretation: Interpretation,
    val knowledgeId: KnowledgeId? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class InterpretationPublished(
    val knowledgeId: KnowledgeId,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
