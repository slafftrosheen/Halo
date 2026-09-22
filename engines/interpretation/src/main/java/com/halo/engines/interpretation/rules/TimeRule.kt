package com.halo.engines.interpretation.rules

import com.halo.cognition.language.ApplicationEntity
import com.halo.cognition.language.CurrentTimeIntent
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.OpenIntent
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.engines.interpretation.parser.TemporalExpressionParser

import com.halo.cognition.language.CurrentDateIntent

class TimeRule : InterpretationRule {
    override val priority: Int = 600

    override suspend fun match(context: InterpretationContext): RuleMatch? {
        val transcript = transcript(context) ?: return null
        val normalizedTranscript = transcript.trim().lowercase().trimEnd('?', '.', '!').trim()
        val consumedTokens = transcript.tokenCount()

        // 1. Explicit current-time query matching
        if (normalizedTranscript in currentTimeForms || currentTimePattern.matches(normalizedTranscript)) {
            return RuleMatch(
                confidence = 1.0f,
                intent = CurrentTimeIntent,
                entities = emptyList(),
                consumedTokens = consumedTokens,
                diagnostics = "Detected explicit current time query"
            )
        }

        // 2. Explicit current-date query matching
        if (normalizedTranscript in currentDateForms || currentDatePattern.matches(normalizedTranscript)) {
            return RuleMatch(
                confidence = 1.0f,
                intent = CurrentDateIntent,
                entities = emptyList(),
                consumedTokens = consumedTokens,
                diagnostics = "Detected explicit current date query"
            )
        }

        // 2. Relative day/time expressions (e.g., "today at 5pm")
        val parser = TemporalExpressionParser()
        val parsedValue = parser.parse(transcript) ?: return null
        
        return RuleMatch(
            confidence = 0.98f,
            entities = listOf(TimeEntity(parsedValue, transcript, 0.98f)),
            consumedTokens = consumedTokens,
            diagnostics = "Extracted time ${parsedValue}"
        )
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun String.tokenCount(): Int =
        trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private companion object {
        val currentTimePattern = Regex(
            "(?:what(?:'s|\\s+is)?(?:\\s+the)?\\s+time|current\\s+time|what\\s+time\\s+is\\s+it|what\\s+time\\s+do\\s+we\\s+have)",
            RegexOption.IGNORE_CASE
        )
        val currentDatePattern = Regex(
            "(?:what(?:'s|\\s+is)?(?:\\s+the)?\\s+date|what\\s+date\\s+is\\s+it|what(?:'s|\\s+is)?\\s+today(?:'s)?\\s+date|what\\s+day\\s+is\\s+it|current\\s+date)",
            RegexOption.IGNORE_CASE
        )
        val currentTimeForms = setOf("time", "the time", "current time")
    }
}
