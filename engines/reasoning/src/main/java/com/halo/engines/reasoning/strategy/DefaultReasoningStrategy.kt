package com.halo.engines.reasoning.strategy

import com.halo.cognition.language.ApplicationEntity
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

/** Deterministic Interpretation -> Goal compiler for executable intents. */
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
                val destination = interpretation.entities
                    .filterIsInstance<LocationEntity>()
                    .firstOrNull()
                    ?.location
                    .orEmpty()
                NavigationPayload(destination)
            }

            is PlayIntent -> {
                val rawTitle = interpretation.entities
                    .filterIsInstance<MediaEntity>()
                    .firstOrNull()
                    ?.title
                    ?: interpretation.transcript
                        .removePrefix("play")
                        .removePrefix("Please")
                        .replace(".", "")
                        .trim()
                val normalizedTitle = rawTitle.replace(".", "").trim()
                val lower = normalizedTitle.lowercase()
                val isGeneric = lower.isEmpty() ||
                    lower == "music" ||
                    lower == "some music" ||
                    lower == "something" ||
                    lower == "a music" ||
                    lower == "start music" ||
                    lower == "start playing" ||
                    lower == "put some music on"
                MediaPayload(
                    query = if (isGeneric) "" else normalizedTitle,
                    action = "play"
                )
            }

            is PauseIntent -> MediaPayload(query = "", action = "pause")

            is ReminderIntent -> MemoryPayload(
                fact = interpretation.entities.firstOrNull()?.toString()
                    ?: interpretation.transcript
            )

            is SearchIntent -> SearchPayload(
                query = interpretation.entities.firstOrNull()?.toString()
                    ?: interpretation.transcript
            )

            is CallIntent -> CommunicationPayload(
                target = interpretation.entities
                    .filterIsInstance<PersonEntity>()
                    .firstOrNull()
                    ?.name
                    .orEmpty()
            )

            is OpenIntent -> {
                val appName = interpretation.entities
                    .filterIsInstance<ApplicationEntity>()
                    .firstOrNull()
                    ?.name
                    ?: interpretation.entities.firstOrNull()?.toString().orEmpty()
                if (appName.equals("time", ignoreCase = true)) {
                    DevicePayload(action = "time", target = "")
                } else {
                    DevicePayload(action = "open", target = appName)
                }
            }

            is ConversationIntent, is QuestionIntent ->
                ConversationPayload(topic = interpretation.transcript)

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
