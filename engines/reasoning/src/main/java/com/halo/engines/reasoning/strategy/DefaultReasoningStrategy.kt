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
import com.halo.cognition.language.ReminderEntity
import com.halo.cognition.language.SearchIntent
import com.halo.cognition.language.SetAlarmIntent
import com.halo.cognition.language.ShowAlarmsIntent
import com.halo.cognition.language.TimeEntity
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

import com.halo.cognition.language.CurrentTimeIntent

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
            is OpenIntent, is CurrentTimeIntent, is SetAlarmIntent, is ShowAlarmsIntent -> GoalType.DEVICE
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

            is ReminderIntent -> {
                val reminder = interpretation.entities.filterIsInstance<ReminderEntity>().firstOrNull()
                MemoryPayload(
                    fact = if (reminder != null) {
                        listOfNotNull(reminder.message, reminder.temporalExpression)
                            .joinToString(REMINDER_FIELD_SEPARATOR)
                    } else interpretation.transcript
                )
            }

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

            is CurrentTimeIntent -> DevicePayload(action = "time", target = "")

            is SetAlarmIntent -> DevicePayload(
                action = "set_alarm",
                target = selectAlarmExpression(interpretation)
            )

            is ShowAlarmsIntent -> DevicePayload(action = "show_alarms", target = "")

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

    /**
     * SpeechRule identifies the command and may contribute the raw temporal phrase,
     * while TimeRule contributes a normalized clock expression. Prefer the normalized
     * form even when its rule confidence is slightly lower; capability execution needs
     * an executable time, not merely the highest-confidence surface phrase.
     */
    private fun selectAlarmExpression(interpretation: Interpretation): String =
        interpretation.entities
            .filterIsInstance<TimeEntity>()
            .sortedWith(
                compareByDescending<TimeEntity> { it.expression.isExecutableClockExpression() }
                    .thenByDescending { it.confidence }
            )
            .firstOrNull()
            ?.expression
            .orEmpty()

    private fun String.isExecutableClockExpression(): Boolean =
        NORMALIZED_ALARM_EXPRESSION.matches(trim())

    private companion object {
        const val REMINDER_FIELD_SEPARATOR = "\u001F"
        val NORMALIZED_ALARM_EXPRESSION = Regex(
            "^(?:(?:today|tomorrow|tonight)\\s+)?(?:[01]?\\d|2[0-3]):[0-5]\\d$",
            RegexOption.IGNORE_CASE
        )
    }
}
