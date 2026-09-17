package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.CallIntent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.LocationEntity
import com.halo.cognition.language.MediaEntity
import com.halo.cognition.language.NavigateIntent
import com.halo.cognition.language.OpenIntent
import com.halo.cognition.language.PauseIntent
import com.halo.cognition.language.PersonEntity
import com.halo.cognition.language.PlayIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.cognition.language.ReminderIntent
import com.halo.cognition.language.SearchIntent
import com.halo.engines.reasoning.service.GoalManager
import com.halo.icl.ontology.CommunicationPayload
import com.halo.icl.ontology.ConversationPayload
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.MediaPayload
import com.halo.icl.ontology.MemoryPayload
import com.halo.icl.ontology.NavigationPayload
import com.halo.icl.ontology.SearchPayload
import com.halo.runtime.cognition.blackboard.Interpretation

/**
 * Deterministic semantic compiler:
 *
 * Interpretation -> Goal
 *
 * This is deliberately rule-based.
 *
 * LLM/semantic escalation can be introduced later as another reasoning
 * strategy, without contaminating the deterministic baseline.
 */
class DefaultReasoningStrategy(
    private val goalManager: GoalManager
) {

    fun generateGoal(
        interpretation: Interpretation,
        parentKnowledgeId: String? = null
    ): Goal {

        val intent = interpretation.intent

        val type = when (intent) {
            is NavigateIntent -> GoalType.NAVIGATION
            is PlayIntent, is PauseIntent -> GoalType.MEDIA
            is ReminderIntent -> GoalType.MEMORY
            is SearchIntent -> GoalType.SEARCH
            is CallIntent -> GoalType.COMMUNICATION
            is OpenIntent -> GoalType.DEVICE
            is QuestionIntent, is ConversationIntent -> GoalType.CONVERSATION
            else -> GoalType.CONVERSATION
        }

        val payload = when (intent) {
            is NavigateIntent -> {
                val dest = interpretation.entities.filterIsInstance<LocationEntity>().firstOrNull()?.location ?: ""
                NavigationPayload(dest)
            }

            is PlayIntent -> {
                // Normalize "play music" and "play some music" to a generic query
                val rawTitle = interpretation.entities.filterIsInstance<MediaEntity>().firstOrNull()?.title ?: ""
                val normalizedTitle = rawTitle.replace(".", "").trim()
                val isGeneric = normalizedTitle.equals("music", ignoreCase = true) || 
                                normalizedTitle.equals("some music", ignoreCase = true)
                MediaPayload(query = if (isGeneric) "" else normalizedTitle, action = "play")
            }

            is PauseIntent -> MediaPayload(query = "", action = "pause")

            is ReminderIntent -> MemoryPayload(
                fact = interpretation.entities.firstOrNull()?.toString() ?: interpretation.transcript
            )

            is SearchIntent -> SearchPayload(
                query = interpretation.entities.firstOrNull()?.toString() ?: interpretation.transcript
            )

            is CallIntent -> CommunicationPayload(
                target = interpretation.entities.filterIsInstance<PersonEntity>().firstOrNull()?.name ?: ""
            )

            is OpenIntent -> DevicePayload(
                action = "open",
                target = interpretation.entities.firstOrNull()?.toString() ?: ""
            )

            is ConversationIntent, is QuestionIntent -> ConversationPayload(
                topic = interpretation.transcript
            )

            else -> ConversationPayload(topic = interpretation.transcript)
        }

        return goalManager.createGoal(
            type = type,
            intent = intent,
            payload = payload,
            confidence = interpretation.confidence,
            parentId = parentKnowledgeId
        )
    }
}
