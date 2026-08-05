package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationBuilder
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.blackboard.Confidence

class SpeechRule : InterpretationRule {
    override val priority: Int = 500

    override suspend fun applies(
        context: InterpretationContext
    ): Boolean = transcript(context) != null

    override suspend fun interpret(
        context: InterpretationContext
    ): Interpretation? {
        val transcript = transcript(context) ?: return null
        val confidence = context.observation.confidence
        val builder = InterpretationBuilder().confidence(
            Confidence(
                lexical = confidence,
                contextual = confidence,
                semantic = 0.99f
            )
        )
        val normalized = transcript.lowercase()

        when {
            normalized.startsWith("call ") || normalized == "call" -> {
                builder.candidate("Call", 0.99f).slot("action", "Call")
                recipient(transcript)?.let {
                    builder.person(it, 0.98f).slot("target", it)
                }
                timeExpression(transcript)?.let { time ->
                    builder
                        .time(time, 0.98f)
                        .slot("time", time)
                        .candidate("Schedule", 0.18f)
                }
            }

            normalized.startsWith("navigate to ") || normalized.startsWith("go to ") -> {
                val destination = navigationPattern.find(transcript)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty()
                if (destination.isBlank()) {
                    return null
                }

                builder
                    .candidate("Navigate", 0.99f)
                    .location(destination.titlecaseWords(), 0.97f)
                    .slot("destination", destination.titlecaseWords())
            }

            normalized.startsWith("open ") -> {
                val application = transcript
                    .dropWhile { !it.isWhitespace() }
                    .trim()
                if (application.isBlank()) {
                    return null
                }

                builder
                    .candidate("OpenApplication", 0.91f)
                    .application(application.titlecaseWords(), 0.91f)
                    .slot("application", application.titlecaseWords())
            }

            else -> return null
        }

        return builder.build()
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun recipient(transcript: String): String? {
        val commandEnd = transcript.indexOfFirst(Char::isWhitespace)
        if (commandEnd < 0) {
            return null
        }

        val remainder = transcript.substring(commandEnd).trim()
        val timeIndex = timePattern.find(remainder)?.range?.first ?: remainder.length
        return remainder
            .substring(0, timeIndex)
            .trim(' ', ',', '.', '!', '?')
            .takeIf { it.isNotBlank() }
            ?.titlecaseWords()
    }

    private fun timeExpression(transcript: String): String? =
        timePattern.find(transcript)?.let { match ->
            normalizeTime(match.groupValues[1], match.groupValues[2])
        }

    private fun normalizeTime(
        day: String,
        rawTime: String
    ): String {
        if (rawTime.isBlank()) {
            return day.lowercase()
        }

        val timeMatch = clockPattern.matchEntire(rawTime.trim()) ?: return "$day $rawTime"
        var hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].ifBlank { "00" }
        val period = timeMatch.groupValues[3].lowercase()

        if (period == "pm" && hour < 12) {
            hour += 12
        } else if (period == "am" && hour == 12) {
            hour = 0
        }

        return "${day.lowercase()} ${hour.toString().padStart(2, '0')}:$minute"
    }

    private fun String.titlecaseWords(): String =
        split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character -> character.titlecase() }
            }

    private companion object {
        val timePattern = Regex(
            "\\b(today|tomorrow|tonight)(?:\\s+at\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?",
            RegexOption.IGNORE_CASE
        )
        val clockPattern = Regex(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            RegexOption.IGNORE_CASE
        )
        val navigationPattern = Regex(
            "^\\s*(?:navigate\\s+to|go\\s+to)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
    }
}
