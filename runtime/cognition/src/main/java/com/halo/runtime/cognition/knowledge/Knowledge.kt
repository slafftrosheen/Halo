package com.halo.runtime.cognition.knowledge

import kotlinx.datetime.Instant

data class Knowledge(
    val id: KnowledgeId,
    val payload: KnowledgePayload,
    val confidence: Confidence,
    val source: KnowledgeSource,
    val createdAt: Instant,
    val expiresAt: Instant? = null
)
