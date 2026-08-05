package com.halo.runtime.cognition.knowledge

import kotlin.reflect.KClass

interface Blackboard {
    suspend fun publish(knowledge: Knowledge)

    suspend fun history(): List<Knowledge>

    suspend fun latest(
        payload: KClass<out KnowledgePayload>
    ): Knowledge?

    suspend fun query(
        query: BlackboardQuery
    ): List<Knowledge>
}
