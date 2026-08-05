package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationResult
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.LowConfidenceInterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.ConversationIntent
import com.halo.engines.interpretation.model.DateEntity
import com.halo.engines.interpretation.model.DeviceEntity
import com.halo.engines.interpretation.model.Entity
import com.halo.engines.interpretation.model.MediaEntity
import com.halo.engines.interpretation.model.NumberEntity
import com.halo.engines.interpretation.model.ObjectEntity
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.PlaceEntity
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.engines.interpretation.model.WebEntity
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.blackboard.Confidence

class RulePipeline(
    private val registry: RuleRegistry,
    private val lowConfidenceRules: List<LowConfidenceInterpretationRule> = emptyList()
) {
    constructor(
        rules: List<InterpretationRule>,
        lowConfidenceRules: List<LowConfidenceInterpretationRule> = emptyList()
    ) : this(RuleRegistry(rules), lowConfidenceRules)

    val ruleCount: Int
        get() = registry.size

    suspend fun execute(context: InterpretationContext): InterpretationResult {
        val matches = mutableListOf<RuleMatch>()
        for (rule in registry.ordered()) {
            rule.evaluate(context)?.let(matches::add)
        }

        var interpretation = buildInterpretation(context, matches)
        for (rule in lowConfidenceRules.sortedByDescending { it.priority }) {
            if (rule.applies(context, interpretation)) {
                rule.interpret(context, interpretation)?.let { match ->
                    matches += match.copy(
                        ruleName = rule::class.java.simpleName,
                        priority = rule.priority
                    )
                    interpretation = buildInterpretation(context, matches)
                }
            }
        }

        return InterpretationResult(
            interpretation = interpretation,
            matches = matches.sortedForMerge()
        )
    }

    private suspend fun InterpretationRule.evaluate(
        context: InterpretationContext
    ): RuleMatch? {
        val startedAt = System.nanoTime()
        val match = match(context) ?: return null
        val latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
        return match.copy(
            ruleName = this::class.java.simpleName,
            priority = priority,
            latencyMillis = latencyMillis
        )
    }

    private fun buildInterpretation(
        context: InterpretationContext,
        matches: List<RuleMatch>
    ): Interpretation {
        val sortedMatches = matches.sortedForMerge()
        val selectedIntentMatch = sortedMatches.firstOrNull { it.intent != null }
        val entities = mergeEntities(sortedMatches)
        val recognizerConfidence = context.observation.confidence.coerceIn(0f, 1f)
        val ruleConfidence = selectedIntentMatch?.confidence ?: FALLBACK_RULE_CONFIDENCE
        val entityConfidence = entities
            .map { it.confidence }
            .average()
            .takeIf { !it.isNaN() }
            ?.toFloat()
            ?: 1f

        return Interpretation(
            transcript = (context.observation.payload as? SpeechRecognizedPayload)
                ?.transcript
                .orEmpty(),
            language = (context.observation.payload as? SpeechRecognizedPayload)
                ?.language
                ?: "und",
            intent = selectedIntentMatch?.intent ?: ConversationIntent,
            entities = entities,
            confidence = Confidence(
                lexical = recognizerConfidence,
                contextual = ruleConfidence,
                semantic = entityConfidence
            ),
            source = context.observation.source,
            metadata = contextMetadata(context)
        )
    }

    private fun List<RuleMatch>.sortedForMerge(): List<RuleMatch> =
        sortedWith(
            compareByDescending<RuleMatch> { it.confidence }
                .thenByDescending { it.priority }
                .thenBy { it.ruleName }
        )

    private fun mergeEntities(matches: List<RuleMatch>): List<Entity> {
        val entities = linkedMapOf<String, Entity>()
        matches.forEach { match ->
            match.entities.forEach { entity ->
                val identity = entity.identity()
                val current = entities[identity]
                if (current == null || entity.confidence > current.confidence) {
                    entities[identity] = entity
                }
            }
        }
        return entities.values.toList()
    }

    private fun contextMetadata(context: InterpretationContext): Map<String, String> =
        buildMap {
            put("contextTime", context.timestamp.toString())
            put("source", context.observation.source.name)
            context.workingMemory.currentLocation?.let {
                put("currentLocation", it.name)
            }
        }

    private fun Entity.identity(): String = when (this) {
        is PersonEntity -> "person:${name.lowercase()}"
        is PlaceEntity -> "place:${name.lowercase()}"
        is TimeEntity -> "time:${expression.lowercase()}"
        is DateEntity -> "date:${expression.lowercase()}"
        is NumberEntity -> "number:$value"
        is ObjectEntity -> "object:${name.lowercase()}"
        is ApplicationEntity -> "application:${name.lowercase()}"
        is DeviceEntity -> "device:${name.lowercase()}"
        is MediaEntity -> "media:${title.lowercase()}"
        is WebEntity -> "web:${query.lowercase()}"
        else -> this::class.java.name
    }

    private companion object {
        const val FALLBACK_RULE_CONFIDENCE = 0.5f
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
