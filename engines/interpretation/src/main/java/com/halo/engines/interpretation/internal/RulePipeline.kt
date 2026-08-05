package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationResult
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.DeviceEntity
import com.halo.engines.interpretation.model.Entity
import com.halo.engines.interpretation.model.IntentCandidate
import com.halo.engines.interpretation.model.LocationEntity
import com.halo.engines.interpretation.model.ObjectEntity
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.Slot
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.runtime.cognition.knowledge.Confidence

class RulePipeline(
    private val registry: RuleRegistry
) {
    constructor(rules: List<InterpretationRule>) : this(RuleRegistry(rules))

    suspend fun execute(
        context: InterpretationContext
    ): InterpretationResult {
        val interpretations = buildList {
            for (rule in registry.all().sortedByDescending { it.priority }) {
                if (rule.applies(context)) {
                    rule.interpret(context)?.let(::add)
                }
            }
        }

        return resultFor(merge(interpretations))
    }

    private fun resultFor(
        interpretation: Interpretation
    ): InterpretationResult {
        if (
            interpretation.entities.isEmpty() &&
            interpretation.candidates.isEmpty() &&
            interpretation.slots.isEmpty()
        ) {
            return InterpretationResult.NoMeaning
        }

        val hasActionableTarget = interpretation.entities.isNotEmpty() ||
            interpretation.slots.any { it.key != "action" }

        return if (interpretation.candidates.isNotEmpty() && hasActionableTarget) {
            InterpretationResult.CompleteMeaning(interpretation)
        } else {
            InterpretationResult.PartialMeaning(interpretation)
        }
    }

    private fun merge(
        interpretations: List<Interpretation>
    ): Interpretation {
        if (interpretations.isEmpty()) {
            return Interpretation()
        }

        val entities = linkedMapOf<String, Entity>()
        val candidates = linkedMapOf<String, IntentCandidate>()
        val slots = linkedMapOf<String, Slot>()

        interpretations.forEach { interpretation ->
            interpretation.entities.forEach { entity ->
                entities.putIfAbsent(entity.identity(), entity)
            }
            interpretation.candidates.forEach { candidate ->
                val current = candidates[candidate.name.lowercase()]
                if (current == null || candidate.confidence > current.confidence) {
                    candidates[candidate.name.lowercase()] = candidate
                }
            }
            interpretation.slots.forEach { slot ->
                slots.putIfAbsent(slot.key.lowercase(), slot)
            }
        }

        return Interpretation(
            entities = entities.values.toList(),
            candidates = candidates.values.sortedByDescending { it.confidence },
            slots = slots.values.toList(),
            confidence = Confidence(
                lexical = interpretations.maxOf { it.confidence.lexical },
                contextual = interpretations.maxOf { it.confidence.contextual },
                semantic = interpretations.maxOf { it.confidence.semantic }
            )
        )
    }

    private fun Entity.identity(): String = when (this) {
        is PersonEntity -> "person:${name.lowercase()}"
        is LocationEntity -> "location:${location.lowercase()}"
        is TimeEntity -> "time:${expression.lowercase()}"
        is ObjectEntity -> "object:${name.lowercase()}"
        is ApplicationEntity -> "application:${name.lowercase()}"
        is DeviceEntity -> "device:${name.lowercase()}"
    }
}
