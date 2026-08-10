package com.halo.engines.reasoning.service

import com.halo.cognition.language.Intent
import com.halo.cognition.language.NavigateIntent
import com.halo.cognition.language.OpenIntent
import com.halo.cognition.language.PauseIntent
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.CallIntent
import com.halo.cognition.language.ReminderIntent
import com.halo.cognition.language.SearchIntent
import com.halo.cognition.language.ConversationIntent
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.Priority
import com.halo.icl.ontology.GoalPayload
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns Goal lifecycle and provenance.
 *
 * GoalManager does not reason.
 * GoalManager does not plan.
 * GoalManager does not execute.
 *
 * It is a state holder / factory.
 */
class GoalManager {

    private val goals = ConcurrentHashMap<String, Goal>()

    fun register(goal: Goal) {
        goals[goal.id.value.toString()] = goal
    }

    fun getGoal(id: String): Goal? =
        goals[id]

    fun getActiveGoals(): List<Goal> =
        goals.values.toList()

    fun createGoal(
        type: GoalType,
        intent: Intent?,
        payload: GoalPayload,
        priority: Priority = Priority.NORMAL,
        confidence: Float = 1.0f,
        parentId: String? = null
    ): Goal {

        val normalizedConfidence =
            confidence.coerceIn(0f, 1f)

        val parentCognitiveId = parentId?.let { idStr ->
            try {
                CognitiveId(UUID.fromString(idStr))
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val goal = Goal(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = Confidence(normalizedConfidence),
            provenance = Provenance(
                generatorId = "engine.reasoning.core",
                parentId = parentCognitiveId
            ),
            edges = emptyList(),
            type = type,
            priority = priority,
            payload = payload
        )

        register(goal)

        return goal
    }
}