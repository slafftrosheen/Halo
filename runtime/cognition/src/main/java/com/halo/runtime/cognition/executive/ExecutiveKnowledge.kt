package com.halo.runtime.cognition.executive

import com.halo.runtime.cognition.blackboard.KnowledgePayload
import kotlinx.datetime.Instant
import com.halo.runtime.cognition.Decision

data class ExecutiveKnowledge(
    val decision: Decision,
    val currentMode: CognitiveMode,
    val timestamp: Instant
) : KnowledgePayload
