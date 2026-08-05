package com.halo.runtime.cognition.blackboard

import kotlinx.datetime.Instant
import kotlin.reflect.KClass

data class BlackboardQuery(
    val payload: KClass<*>? = null,
    val source: KnowledgeSource? = null,
    val minimumConfidence: Float? = null,
    val since: Instant? = null,
    val until: Instant? = null
) {
    fun matches(knowledge: Knowledge): Boolean =
        (payload == null || payload.isInstance(knowledge.payload)) &&
            (source == null || source == knowledge.source) &&
            (minimumConfidence == null ||
                knowledge.confidence.overall >= minimumConfidence) &&
            (since == null || knowledge.createdAt >= since) &&
            (until == null || knowledge.createdAt <= until)
}
