package com.halo.engines.interpretation.api

import com.halo.engines.interpretation.model.ApplicationEntity
import com.halo.engines.interpretation.model.CallIntent
import com.halo.engines.interpretation.model.ConversationIntent
import com.halo.engines.interpretation.model.Entity
import com.halo.engines.interpretation.model.Intent
import com.halo.engines.interpretation.model.NavigateIntent
import com.halo.engines.interpretation.model.OpenIntent
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.interpretation.model.PlaceEntity
import com.halo.engines.interpretation.model.PlayIntent
import com.halo.engines.interpretation.model.ReminderIntent
import com.halo.engines.interpretation.model.SearchIntent
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.sdk.runtime.Source

class InterpretationBuilder {
    private val entities = mutableListOf<Entity>()
    private val metadata = linkedMapOf<String, String>()
    private var intent: Intent = ConversationIntent
    private var confidence = Confidence.None
    private var transcript = ""
    private var language = "und"
    private var source = Source.USER

    fun intent(intent: Intent): InterpretationBuilder = apply {
        this.intent = intent
    }

    fun entity(entity: Entity): InterpretationBuilder = apply {
        entities += entity
    }

    fun person(
        name: String,
        confidence: Float
    ): InterpretationBuilder = entity(PersonEntity(name, confidence))

    fun place(
        name: String,
        confidence: Float
    ): InterpretationBuilder = entity(PlaceEntity(name, confidence))

    fun application(
        name: String,
        confidence: Float
    ): InterpretationBuilder = entity(ApplicationEntity(name, confidence))

    fun time(
        expression: String,
        confidence: Float
    ): InterpretationBuilder = entity(TimeEntity(expression, confidence))

    fun transcript(transcript: String): InterpretationBuilder = apply {
        this.transcript = transcript
    }

    fun language(language: String): InterpretationBuilder = apply {
        this.language = language
    }

    fun source(source: Source): InterpretationBuilder = apply {
        this.source = source
    }

    fun metadata(key: String, value: String): InterpretationBuilder = apply {
        metadata[key] = value
    }

    fun confidence(confidence: Confidence): InterpretationBuilder = apply {
        this.confidence = confidence
    }

    @Deprecated("Use intent(Intent)")
    fun candidate(
        name: String,
        confidence: Float
    ): InterpretationBuilder = intent(name.toIntent()).confidence(
        Confidence(
            lexical = confidence,
            contextual = confidence,
            semantic = confidence
        )
    )

    @Deprecated("Use metadata(key, value)")
    fun slot(
        key: String,
        value: Any
    ): InterpretationBuilder = metadata("legacy.$key", value.toString())

    fun build(): Interpretation = Interpretation(
        transcript = transcript,
        language = language,
        intent = intent,
        entities = entities.toList(),
        confidence = confidence,
        source = source,
        metadata = metadata.toMap()
    )

    private fun String.toIntent(): Intent = when (lowercase()) {
        "call" -> CallIntent
        "navigate" -> NavigateIntent
        "open", "openapplication" -> OpenIntent
        "play" -> PlayIntent
        "remind", "reminder", "schedule" -> ReminderIntent
        "search" -> SearchIntent
        else -> ConversationIntent
    }
}
