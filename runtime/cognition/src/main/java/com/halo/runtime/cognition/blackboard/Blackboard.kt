package com.halo.runtime.cognition.blackboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

interface Blackboard {
    val flow: StateFlow<List<Knowledge>>

    suspend fun publish(
        knowledge: Knowledge
    )

    suspend fun latest(
        type: KClass<out KnowledgePayload>
    ): Knowledge?

    suspend fun query(
        query: BlackboardQuery
    ): List<Knowledge>

    fun stream(): Flow<Knowledge>
}
