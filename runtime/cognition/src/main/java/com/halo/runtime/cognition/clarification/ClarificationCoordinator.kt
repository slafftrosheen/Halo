package com.halo.runtime.cognition.clarification

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
    data class Resolved(val intent: Intent, val entities: List<Entity>) : ClarificationResolution
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

    private val _pending = MutableStateFlow<PendingClarification?>(null)
    override val pending: StateFlow<PendingClarification?> = _pending.asStateFlow()

    override suspend fun request(value: PendingClarification) {
        _pending.value = value
    }

    override suspend fun consume(transcript: String, now: Instant): ClarificationResolution? {
        val current = _pending.value ?: return null
        if (now.isAfter(current.expiresAt)) {
            _pending.value = null
            return null
        }

        if (current.intent is SetAlarmIntent) {
            val parseResult = parser.parse(transcript)
            if (parseResult is TemporalParseResult.Parsed) {
                _pending.value = null
                val timeEntity = TimeEntity(parseResult.value, transcript, 0.99f)
                val combinedEntities = current.entities.filterNot { it is TimeEntity } + timeEntity
                return ClarificationResolution.Resolved(current.intent, combinedEntities)
            }
        }

        return ClarificationResolution.Unresolved
    }

    override suspend fun cancel(reason: String) {
        _pending.value = null
    }
}
