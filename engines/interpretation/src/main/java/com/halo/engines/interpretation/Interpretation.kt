package com.halo.engines.interpretation

import com.halo.sdk.runtime.HaloEvent
import com.halo.runtime.cognition.knowledge.KnowledgeId

@JvmInline
value class InterpretationId(val value: String)

typealias Interpretation = com.halo.engines.interpretation.model.Interpretation

data class InterpretationCreated(
    val interpretation: Interpretation,
    val knowledgeId: KnowledgeId? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
