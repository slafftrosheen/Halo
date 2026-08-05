package com.halo.runtime.cognition.blackboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

class DefaultBlackboard : Blackboard {
    private val mutex = Mutex()
    private val knowledge = mutableListOf<Knowledge>()
    private val updates = MutableSharedFlow<Knowledge>(
        replay = 0,
        extraBufferCapacity = 512
    )

    override suspend fun publish(
        knowledge: Knowledge
    ) {
        mutex.withLock {
            this.knowledge += knowledge
            updates.emit(knowledge)
        }
    }

    override suspend fun latest(
        type: KClass<out KnowledgePayload>
    ): Knowledge? = mutex.withLock {
        knowledge.asReversed().firstOrNull {
            type.isInstance(it.payload)
        }
    }

    override suspend fun query(
        query: BlackboardQuery
    ): List<Knowledge> = mutex.withLock {
        knowledge.filter(query::matches)
    }

    override fun stream(): Flow<Knowledge> = updates.asSharedFlow()
}
