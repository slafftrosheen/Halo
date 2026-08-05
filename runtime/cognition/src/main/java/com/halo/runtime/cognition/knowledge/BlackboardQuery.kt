package com.halo.runtime.cognition.knowledge

import kotlinx.datetime.Instant
import kotlin.reflect.KClass

data class BlackboardQuery(
    val payload: KClass<*>? = null,
    val source: KnowledgeSource? = null,
    val minimumConfidence: Float? = null,
    val since: Instant? = null,
    val until: Instant? = null
)
