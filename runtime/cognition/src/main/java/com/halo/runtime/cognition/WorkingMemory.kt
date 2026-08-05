package com.halo.runtime.cognition

import com.halo.runtime.cognition.knowledge.Blackboard as KnowledgeBlackboard
import com.halo.runtime.cognition.knowledge.InMemoryBlackboard
import com.halo.runtime.cognition.knowledge.InterpretationPayload
import com.halo.runtime.cognition.knowledge.LocationEntity
import com.halo.runtime.cognition.knowledge.ObservationPayload as ObservationKnowledgePayload
import com.halo.runtime.cognition.knowledge.PersonEntity
import com.halo.runtime.cognition.knowledge.TaskPayload
import com.halo.runtime.cognition.knowledge.GoalPayload
import com.halo.runtime.cognition.knowledge.BlackboardQuery
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Source

class WorkingMemory(
    private val blackboard: KnowledgeBlackboard = InMemoryBlackboard()
) {
    suspend fun currentGoal(): Goal? =
        (blackboard.latest(GoalPayload::class)?.payload as? GoalPayload)?.goal

    suspend fun recentObservations(limit: Int = 10): List<Observation> {
        require(limit >= 0) { "limit must not be negative" }

        return blackboard.query(BlackboardQuery(payload = ObservationKnowledgePayload::class))
            .mapNotNull { (it.payload as? ObservationKnowledgePayload)?.observation }
            .takeLast(limit)
    }

    suspend fun activeTasks(): List<Task> {
        val latestTasks = linkedMapOf<java.util.UUID, Task>()

        blackboard.query(BlackboardQuery(payload = TaskPayload::class))
            .mapNotNull { (it.payload as? TaskPayload)?.task }
            .forEach { task ->
                latestTasks.remove(task.id)
                latestTasks[task.id] = task
            }

        return latestTasks.values.filter {
            it.state == TaskState.PENDING || it.state == TaskState.RUNNING
        }
    }

    suspend fun currentLocation(): LocationEntity? =
        latestEntity { it as? LocationEntity }

    suspend fun lastSpeech(): Observation? =
        recentObservations(Int.MAX_VALUE)
            .asReversed()
            .firstOrNull {
                it.modality == Modality.AUDIO &&
                    it.source == Source.MICROPHONE
            }

    suspend fun focusedPerson(): PersonEntity? =
        latestEntity { it as? PersonEntity }

    private suspend fun <T> latestEntity(selector: (Any) -> T?): T? {
        for (knowledge in blackboard.history().asReversed()) {
            val interpretation =
                (knowledge.payload as? InterpretationPayload)?.interpretation ?: continue

            for (entity in interpretation.entities) {
                selector(entity)?.let { return it }
            }
        }

        return null
    }
}
