package com.halo.runtime.cognition.workingmemory

import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.TaskState
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.BlackboardQuery
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.blackboard.InterpretationPayload
import com.halo.runtime.cognition.blackboard.LocationEntity
import com.halo.runtime.cognition.blackboard.ObservationPayload
import com.halo.runtime.cognition.blackboard.PersonEntity
import com.halo.runtime.cognition.blackboard.GoalPayload
import com.halo.runtime.cognition.blackboard.TaskPayload
import com.halo.sdk.runtime.Observation

class DefaultWorkingMemory(
    private val blackboard: Blackboard
) : WorkingMemory {
    override suspend fun currentGoal(): Goal? =
        (blackboard.latest(GoalPayload::class)?.payload as? GoalPayload)?.goal

    override suspend fun activeTask(): Task? =
        activeTasks().lastOrNull()

    override suspend fun latestObservation(): Observation? =
        (blackboard.latest(ObservationPayload::class)?.payload as? ObservationPayload)
            ?.observation

    override suspend fun latestInterpretation(): Interpretation? =
        (blackboard.latest(InterpretationPayload::class)?.payload as? InterpretationPayload)
            ?.interpretation

    override suspend fun focusedPerson(): PersonEntity? =
        latestEntity { it as? PersonEntity }

    override suspend fun currentLocation(): LocationEntity? =
        latestEntity { it as? LocationEntity }

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

    private suspend fun <T> latestEntity(
        selector: (Any) -> T?
    ): T? {
        for (knowledge in blackboard.query(
            BlackboardQuery(payload = InterpretationPayload::class)
        ).asReversed()) {
            val interpretation =
                (knowledge.payload as? InterpretationPayload)?.interpretation ?: continue

            for (entity in interpretation.entities) {
                selector(entity)?.let { return it }
            }
        }

        return null
    }
}
