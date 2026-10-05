package com.halo.runtime.cognition.clarification

import com.halo.cognition.language.ReminderIntent
import com.halo.cognition.language.ReminderEntity
import com.halo.cognition.language.UnresolvedTimeEntity
import com.halo.cognition.language.Entity
import com.halo.cognition.language.Intent
import com.halo.cognition.language.InteractionId
import com.halo.cognition.language.SetAlarmIntent
import com.halo.cognition.language.TemporalParseResult
import com.halo.cognition.language.TemporalValue
import com.halo.cognition.language.TemporalExpressionParser
import com.halo.cognition.language.TimeEntity
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.SpeechObservationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class PendingClarification(
    val id: String,
    val interactionId: InteractionId,
    val intent: Intent,
    val field: String,
    val entities: List<Entity>,
    val originalTranscript: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)

sealed interface ClarificationResolution {
    data class Resolved(val intent: Intent, val entities: List<Entity>, val interactionId: InteractionId? = null) : ClarificationResolution
    data object Unresolved : ClarificationResolution
}

interface ClarificationCoordinator {
    val pending: StateFlow<PendingClarification?>
    suspend fun request(value: PendingClarification)
    suspend fun consume(transcript: String, now: Instant = Instant.now()): ClarificationResolution?
    suspend fun cancel(reason: String)
}

class DefaultClarificationCoordinator(
    private val parser: TemporalExpressionParser = TemporalExpressionParser()
) : ClarificationCoordinator {

    private val mutex = Mutex()
    private val _pending = MutableStateFlow<PendingClarification?>(null)
    override val pending: StateFlow<PendingClarification?> = _pending.asStateFlow()

    override suspend fun request(value: PendingClarification) = mutex.withLock {
        _pending.value = value
    }

    override suspend fun consume(transcript: String, now: Instant): ClarificationResolution? = mutex.withLock {
        val current = _pending.value ?: return@withLock null
        if (!now.isBefore(current.expiresAt)) {
            _pending.value = null
            return@withLock null
        }

        if (transcript.trim().lowercase() in setOf("cancel", "cancel it", "never mind", "nevermind")) {
            _pending.value = null
            return@withLock null
        }
        if (current.intent is SetAlarmIntent || current.intent is ReminderIntent) {
            val reply = transcript.trim().lowercase().trimEnd('.', '!', '?')
            val originalTime = current.entities.filterIsInstance<UnresolvedTimeEntity>().firstOrNull()?.originalText
                ?: current.entities.filterIsInstance<ReminderEntity>().firstOrNull()?.temporalExpression.orEmpty()
            val expression = when (reply) {
                "evening", "afternoon", "pm", "in the evening" -> "$originalTime pm"
                "morning", "am", "in the morning" -> "$originalTime am"
                else -> reply
            }
            val canonical = if (current.intent is ReminderIntent) com.halo.cognition.language.ReminderTemporalValidation.canonical(expression) else null
            val parseResult = parser.parse(canonical ?: expression)
            val parsedTime = (parseResult as? TemporalParseResult.Parsed)?.value as? TemporalValue.TimeOfDay
            if (parsedTime != null && (current.intent !is ReminderIntent || com.halo.cognition.language.ReminderTemporalValidation.canonical(expression) != null)) {
                _pending.value = null
                val originalDay = current.entities.filterIsInstance<TimeEntity>().firstOrNull()?.value.let {
                    when (it) {
                        is TemporalValue.DayPart -> it.dayRelation
                        is TemporalValue.TimeOfDay -> it.dayRelation
                        else -> if (originalTime.contains("tomorrow", true)) com.halo.cognition.language.DayRelation.TOMORROW
                            else if (originalTime.contains("today", true)) com.halo.cognition.language.DayRelation.TODAY else null
                    }
                }
                val resolvedTime = parsedTime.copy(dayRelation = parsedTime.dayRelation ?: originalDay)
                val timeEntity = TimeEntity(resolvedTime, expression, 0.99f)
                val combinedEntities = current.entities.filterNot { it is TimeEntity || it is UnresolvedTimeEntity }.map {
                    if (it is ReminderEntity) {
                        val value = resolvedTime
                        val day = value.dayRelation?.name?.lowercase()?.plus(" ").orEmpty()
                        it.copy(temporalExpression = "${day}at ${value.time}")
                    } else it
                } + timeEntity
                return@withLock ClarificationResolution.Resolved(current.intent, combinedEntities, current.interactionId)
            }
        }

        return@withLock ClarificationResolution.Unresolved
    }

    override suspend fun cancel(reason: String) = mutex.withLock {
        _pending.value = null
    }
}
