package com.halo.engines.interpretation.api

import com.halo.engines.interpretation.model.Entity
import com.halo.engines.interpretation.model.IntentCandidate
import com.halo.engines.interpretation.model.LocationEntity
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.Slot
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.runtime.cognition.knowledge.Confidence

class InterpretationBuilder {
    private val entities = mutableListOf<Entity>()
    private val candidates = mutableListOf<IntentCandidate>()
    private val slots = mutableListOf<Slot>()
    private var confidence = Confidence.None

    fun entity(entity: Entity): InterpretationBuilder = apply {
        entities += entity
    }

    fun person(
        name: String,
        confidence: Float
    ): InterpretationBuilder = entity(PersonEntity(name, confidence))

    fun location(
        location: String,
        confidence: Float
    ): InterpretationBuilder = entity(LocationEntity(location, confidence))

    fun time(
        expression: String,
        confidence: Float
    ): InterpretationBuilder = entity(TimeEntity(expression, confidence))

    fun slot(
        key: String,
        value: Any
    ): InterpretationBuilder = apply {
        slots += Slot(key, value)
    }

    fun candidate(
        name: String,
        confidence: Float
    ): InterpretationBuilder = apply {
        candidates += IntentCandidate(name, confidence)
    }

    fun confidence(confidence: Confidence): InterpretationBuilder = apply {
        this.confidence = confidence
    }

    fun build(): Interpretation = Interpretation(
        entities = entities.toList(),
        candidates = candidates.toList(),
        slots = slots.toList(),
        confidence = confidence
    )
}
