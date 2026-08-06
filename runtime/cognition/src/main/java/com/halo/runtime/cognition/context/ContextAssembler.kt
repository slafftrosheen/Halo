package com.halo.runtime.cognition.context

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.TaskState
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.BlackboardQuery
import com.halo.runtime.cognition.blackboard.GoalKnowledge
import com.halo.runtime.cognition.blackboard.TaskPayload
import com.halo.runtime.cognition.workingmemory.WorkingMemory
import com.halo.runtime.cognition.workingmemory.snapshot
import com.halo.sdk.runtime.Observation
import kotlinx.datetime.Instant

interface ContextAssembler {
    suspend fun build(
        observation: Observation
    ): CognitiveContext
}

class DefaultContextAssembler(
    private val blackboard: Blackboard,
    private val workingMemory: WorkingMemory,
    private val recentKnowledgeLimit: Int = 20
) : ContextAssembler {
    init {
        require(recentKnowledgeLimit >= 0) {
            "recentKnowledgeLimit must not be negative"
        }
    }

    override suspend fun build(
        observation: Observation
    ): CognitiveContext {
        val recentKnowledge = blackboard.query(BlackboardQuery())
            .takeLast(recentKnowledgeLimit)
        val goals = blackboard.query(BlackboardQuery(payload = GoalKnowledge::class))
            .mapNotNull { (it.payload as? GoalKnowledge)?.goal }
        val tasks = activeTasks()

        return CognitiveContext(
            observation = observation,
            workingMemory = workingMemory.snapshot(recentKnowledge),
            goals = goals,
            tasks = tasks,
            timestamp = Instant.fromEpochMilliseconds(observation.timestamp)
        )
    }

    private suspend fun activeTasks(): List<Task> {
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
}
