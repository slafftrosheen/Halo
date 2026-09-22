package com.halo.engines.interpretation.rules

import com.halo.cognition.language.DayPartKind
import com.halo.cognition.language.PersonEntity
import com.halo.cognition.language.TemporalExpressionParser
import com.halo.cognition.language.TemporalValue
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.CallIntent
import com.halo.engines.interpretation.model.MediaEntity
import com.halo.engines.interpretation.model.NavigateIntent
import com.halo.engines.interpretation.model.OpenIntent
import com.halo.engines.interpretation.model.PauseIntent
import com.halo.engines.interpretation.model.PlaceEntity
import com.halo.engines.interpretation.model.PlayIntent
import com.halo.engines.interpretation.model.QuestionIntent
import com.halo.engines.interpretation.model.SearchIntent
import com.halo.engines.interpretation.model.SetAlarmIntent
import com.halo.engines.interpretation.model.ShowAlarmsIntent
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.engines.interpretation.model.WebEntity
import com.halo.engines.speech.SpeechRecognizedPayload

class SpeechRule : InterpretationRule {
    override val priority: Int = 500

    override suspend fun match(context: InterpretationContext): RuleMatch? {
        val transcript = transcript(context) ?: return null
        val normalized = transcript.lowercase()
        val confidence = context.observation.confidence
        val consumedTokens = transcript.tokenCount()

        return when {
            normalized.startsWith("call ") || normalized.startsWith("phone ") || normalized == "call" -> {
                val target = transcript.afterFirstWord().trim()
                RuleMatch(
                    confidence = 0.99f,
                    intent = CallIntent,
                    entities = if (target.isNotBlank()) listOf(PersonEntity(target.titlecaseWords(), 0.99f)) else emptyList(),
                    consumedTokens = consumedTokens,
                    diagnostics = "Detected call command"
                )
            }

            navigationPattern.containsMatchIn(transcript) -> {
                val destination = navigationPattern.find(transcript)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty()
                destination.takeIf { it.isNotBlank() }?.let {
                    RuleMatch(
                        confidence = 0.99f,
                        intent = NavigateIntent,
                        entities = listOf(PlaceEntity(it.titlecaseWords(), 0.97f)),
                        consumedTokens = consumedTokens,
                        diagnostics = "Detected navigation command"
                    )
                }
            }

            showAlarmsPattern.matches(normalized) -> RuleMatch(
                confidence = 0.99f,
                intent = ShowAlarmsIntent,
                consumedTokens = consumedTokens,
                diagnostics = "Detected show alarms command"
            )

            normalized.startsWith("open ") || normalized.startsWith("launch ") || normalized.startsWith("start ") -> {
                val application = transcript.afterFirstWord()
                application.takeIf { it.isNotBlank() }?.let {
                    RuleMatch(
                        confidence = 0.95f,
                        intent = OpenIntent,
                        entities = listOf(ApplicationEntity(it.titlecaseWords(), 0.94f)),
                        consumedTokens = consumedTokens,
                        diagnostics = "Detected application command"
                    )
                }
            }

            normalized.startsWith("play ") -> {
                val media = transcript.afterFirstWord()
                media.takeIf { it.isNotBlank() }?.let {
                    RuleMatch(
                        confidence = 0.95f,
                        intent = PlayIntent,
                        entities = listOf(MediaEntity(it.titlecaseWords(), 0.93f)),
                        consumedTokens = consumedTokens,
                        diagnostics = "Detected media command"
                    )
                }
            }

            normalized == "pause" || normalized.startsWith("pause ") || normalized == "stop music" -> RuleMatch(
                confidence = 0.95f,
                intent = PauseIntent,
                consumedTokens = consumedTokens,
                diagnostics = "Detected pause command"
            )

            alarmPattern.matches(transcript) -> {
                val expression = alarmPattern.matchEntire(transcript)?.groupValues?.get(1)?.trim().orEmpty()
                val parsedValue = TemporalExpressionParser().parse(expression)
                    ?: TemporalValue.DayPart(DayPartKind.MORNING)
                RuleMatch(
                    confidence = 0.99f,
                    intent = SetAlarmIntent,
                    entities = listOf(TimeEntity(parsedValue, expression, 0.99f)),
                    consumedTokens = consumedTokens,
                    diagnostics = "Detected alarm command"
                )
            }

            searchPattern.containsMatchIn(transcript) -> {
                val query = searchPattern.find(transcript)?.groupValues?.get(1)?.trim().orEmpty()
                query.takeIf { it.isNotBlank() }?.let {
                    RuleMatch(
                        confidence = 0.96f,
                        intent = SearchIntent,
                        entities = listOf(WebEntity(it, 0.95f)),
                        consumedTokens = consumedTokens,
                        diagnostics = "Detected web search command"
                    )
                }
            }

            questionPattern.containsMatchIn(transcript) -> RuleMatch(
                confidence = confidence.coerceAtLeast(0.7f),
                intent = QuestionIntent,
                consumedTokens = consumedTokens,
                diagnostics = "Detected question"
            )

            else -> null
        }
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun String.afterFirstWord(): String =
        dropWhile { !it.isWhitespace() }.trim()

    private fun String.tokenCount(): Int =
        trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun String.titlecaseWords(): String =
        split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character -> character.titlecase() }
            }

    private companion object {
        val searchPattern = Regex(
            "^\\s*(?:search(?:\\s+for)?|google|look\\s+up|find\\s+information\\s+about)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val navigationPattern = Regex(
            "^\\s*(?:navigate(?:\\s+to)?|directions\\s+to|take\\s+me\\s+to|go\\s+to|open\\s+directions\\s+to)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val questionPattern = Regex(
            "^\\s*(?:what|when|where|who|why|how|can|could|would|is|are|do|does)\\b|\\?$",
            RegexOption.IGNORE_CASE
        )
        val alarmPattern = Regex(
            "^\\s*(?:set (?:an? )?alarm(?: for)?|alarm for|wake me at)\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )
        val showAlarmsPattern = Regex(
            "^\\s*(?:(?:show|open)(?: my)? alarms|upcoming alarms)\\s*$",
            RegexOption.IGNORE_CASE
        )
    }
}
