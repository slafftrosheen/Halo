package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.ReminderEntity
import com.halo.engines.interpretation.model.ReminderIntent
import com.halo.engines.speech.SpeechRecognizedPayload

class ReminderRule : InterpretationRule {
    override val priority: Int = 700

    override suspend fun match(context: InterpretationContext): RuleMatch? {
        val transcript = (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript?.trim()?.trimEnd('.', '!', '?')?.takeIf(String::isNotEmpty) ?: return null
        val match = patterns.firstNotNullOfOrNull { it.matchEntire(transcript) } ?: return null
        val message = match.groups["message"]?.value?.trim(' ', ',', ':')
            ?.takeIf(String::isNotBlank) ?: return null
        val temporal = match.groups["time"]?.value?.trim(' ', ',', ':')
            ?.takeIf(String::isNotBlank)
        return RuleMatch(
            confidence = 0.99f,
            intent = ReminderIntent,
            entities = listOf(ReminderEntity(message, temporal, 0.99f)),
            consumedTokens = transcript.split(Regex("\\s+")).size,
            diagnostics = "Detected structured reminder"
        )
    }

    private companion object {
        val patterns = listOf(
            Regex("^remind me (?<time>(?:today|tomorrow|tonight)(?:\\s+(?:(?:at|in)\\s+.+?|morning|afternoon|evening|night))?) to (?<message>.+)$", RegexOption.IGNORE_CASE),
            Regex("^remind me (?<time>(?:at|in)\\s+.+?) to (?<message>.+)$", RegexOption.IGNORE_CASE),
            Regex("^remind me to (?<message>.+?)(?:\\s+(?<time>(?:(?:today|tomorrow|tonight)(?:\\s+.+)?|(?:at|in)\\s+.+)))?$", RegexOption.IGNORE_CASE),
            Regex("^set (?:a )?reminder (?:to|for) (?<message>.+?)(?:[, ]+(?<time>(?:today|tomorrow|tonight)?\\s*(?:at|in)\\s+.+))$", RegexOption.IGNORE_CASE),
            Regex("^reminder (?<time>(?:today|tomorrow|tonight)?\\s*(?:at|in)\\s+.+?):\\s*(?<message>.+)$", RegexOption.IGNORE_CASE)
        )
    }
}
