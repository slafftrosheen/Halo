package com.halo.runtime.cognition.blackboard

import kotlinx.datetime.Instant

@JvmInline
value class KnowledgeId(
    val value: String
)

enum class KnowledgeSource {
    AUDIO,
    CAMERA,
    OCR,
    GPS,
    SENSOR,
    AI,
    RULE,
    MEMORY,
    USER
}

data class Knowledge(
    val id: KnowledgeId,
    val payload: KnowledgePayload,
    val confidence: Confidence,
    val source: KnowledgeSource,
    val createdAt: Instant,
    val expiresAt: Instant? = null
)
