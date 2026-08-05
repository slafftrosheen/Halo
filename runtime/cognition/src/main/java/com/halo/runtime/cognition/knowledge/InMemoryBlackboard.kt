package com.halo.runtime.cognition.knowledge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

class InMemoryBlackboard : Blackboard {
    private val mutex = Mutex()
    private val entries = mutableListOf<Knowledge>()

    override suspend fun publish(knowledge: Knowledge) {
        mutex.withLock {
            entries += knowledge
        }
    }

    override suspend fun history(): List<Knowledge> = mutex.withLock {
        entries.toList()
    }

    override suspend fun latest(
        payload: KClass<out KnowledgePayload>
    ): Knowledge? = mutex.withLock {
        entries.asReversed().firstOrNull { payload.isInstance(it.payload) }
    }

    override suspend fun query(
        query: BlackboardQuery
    ): List<Knowledge> = mutex.withLock {
        entries.filter { knowledge ->
            (query.payload == null || query.payload.isInstance(knowledge.payload)) &&
                (query.source == null || query.source == knowledge.source) &&
                (query.minimumConfidence == null ||
                    knowledge.confidence.overall >= query.minimumConfidence) &&
                (query.since == null || knowledge.createdAt >= query.since) &&
                (query.until == null || knowledge.createdAt <= query.until)
        }
    }
}
