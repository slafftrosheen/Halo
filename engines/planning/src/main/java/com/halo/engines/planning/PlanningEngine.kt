package com.halo.engines.planning

import com.halo.icl.ontology.CommunicationPayload
import com.halo.icl.ontology.ConversationPayload
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.MemoryPayload
import com.halo.icl.ontology.MediaPayload
import com.halo.icl.ontology.NavigationPayload
import com.halo.icl.ontology.SearchPayload
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.SystemTask
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WebTask

/**
 * Deterministic Goal -> ExecutionPlan compiler.
 *
 * Planning does not execute anything.
 *
 * Responsibilities:
 *   Goal -> one or more typed runtime Tasks
 *
 * It must never:
 *   - call Android APIs
 *   - invoke capabilities
 *   - call an LLM
 *   - publish execution results
 */
class PlanningEngine {

    fun createPlan(goal: Goal): ExecutionPlan {
        val task = createTask(goal)

        return ExecutionPlan(
            goal = goal,
            tasks = listOf(task)
        )
    }

    private fun createTask(goal: Goal): Task {
        return when (goal.type) {

            GoalType.CONVERSATION -> {
                val payload = goal.payload as? ConversationPayload
                    ?: return unsupportedGoal(goal)

                CommunicationTask(
                    targetId = "user",
                    payload = payload.topic.orEmpty()
                )
            }

            GoalType.COMMUNICATION -> {
                val payload = goal.payload as? CommunicationPayload
                    ?: return unsupportedGoal(goal)

                CommunicationTask(
                    targetId = payload.target,
                    payload = payload.message.orEmpty()
                )
            }

            GoalType.NAVIGATION -> {
                val payload = goal.payload as? NavigationPayload
                    ?: return unsupportedGoal(goal)

                NavigationTask(
                    coordinates = payload.destination
                )
            }

            GoalType.MEDIA -> {
                val payload = goal.payload as? MediaPayload
                    ?: return unsupportedGoal(goal)

                MediaTask(
                    mediaAction = payload.action,
                    mediaId = payload.query
                )
            }

            GoalType.DEVICE -> {
                val payload = goal.payload as? DevicePayload
                    ?: return unsupportedGoal(goal)

                DeviceTask(
                    action = payload.action
                )
            }

            GoalType.MEMORY -> {
                val payload = goal.payload as? MemoryPayload
                    ?: return unsupportedGoal(goal)

                MemoryTask(
                    memoryAction = payload.fact
                )
            }

            GoalType.SEARCH -> {
                val payload = goal.payload as? SearchPayload
                    ?: return unsupportedGoal(goal)

                WebTask(
                    url = payload.query
                )
            }

            GoalType.AUTOMATION -> {
                unsupportedGoal(goal)
            }
        }
    }

    /**
     * Planning must not invent an executable command for an unsupported
     * semantic goal.
     *
     * A SystemTask with a textual "Execute Goal..." command is dangerous
     * because it looks executable while containing no actual capability
     * contract.
     */
    private fun unsupportedGoal(goal: Goal): SystemTask {
        return SystemTask(
            command = "UNSUPPORTED_GOAL:${goal.type}"
        )
    }
}