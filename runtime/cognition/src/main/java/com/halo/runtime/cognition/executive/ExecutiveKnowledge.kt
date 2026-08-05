package com.halo.runtime.cognition.executive

import com.halo.runtime.cognition.blackboard.KnowledgePayload
import com.halo.runtime.cognition.AttentionState
import kotlinx.datetime.Instant
import com.halo.runtime.cognition.Decision

data class ExecutiveKnowledge(
    val decision: Decision,
    val currentMode: CognitiveMode,
    val updatedAttention: AttentionState,
    val timestamp: Instant
) : KnowledgePayload
