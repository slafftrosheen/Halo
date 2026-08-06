package com.halo.engines.reasoning.strategy

import com.halo.engines.reasoning.service.GoalManager
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.cognition.language.*
import com.halo.icl.ontology.*

class DefaultReasoningStrategy(private val goalManager: GoalManager) {

    fun generateGoal(interpretation: Interpretation): Goal {
        val type = when (interpretation.intent) {
            is NavigateIntent -> GoalType.NAVIGATION
            is PlayIntent, is PauseIntent -> GoalType.MEDIA
            is ReminderIntent -> GoalType.MEMORY
            is SearchIntent -> GoalType.SEARCH
            is CallIntent -> GoalType.COMMUNICATION
            is OpenIntent -> GoalType.DEVICE
            else -> GoalType.CONVERSATION
        }

        val payload = when (interpretation.intent) {
            is NavigateIntent -> NavigationPayload(destination = extractEntity<PlaceEntity>(interpretation.entities)?.name ?: "")
            is PlayIntent -> MediaPayload(query = extractEntity<MediaEntity>(interpretation.entities)?.title ?: "", action = "play")
            is PauseIntent -> MediaPayload(query = "", action = "pause")
            is ReminderIntent -> MemoryPayload(fact = "") // Just mock memory/reminder
            is SearchIntent -> SearchPayload(query = interpretation.transcript)
            is CallIntent -> CommunicationPayload(target = extractEntity<PersonEntity>(interpretation.entities)?.name ?: "")
            is OpenIntent -> DevicePayload(action = "open", target = extractEntity<ApplicationEntity>(interpretation.entities)?.name ?: "")
            else -> ConversationPayload(topic = interpretation.transcript)
        }

        return goalManager.createGoal(
            type = type,
            intent = interpretation.intent,
            payload = payload,
            confidence = interpretation.confidence
        )
    }

    private inline fun <reified T : Entity> extractEntity(entities: List<Entity>): T? {
        return entities.filterIsInstance<T>().firstOrNull()
    }
}
