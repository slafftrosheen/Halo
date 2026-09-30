package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.TemporalInputMode

/**
 * RUNTIME-019 pure validator: every surface is validated before publication.
 *
 * Enforces bounded content (UX + safety), structural consistency between
 * [InteractionSurfaceKind] and [InteractionContent], unique stable ids, and
 * coherent expiry. Rejects malformed or unbounded data — including anything
 * an LLM might have smuggled into surface fields. No host knowledge.
 */
object InteractionSurfaceValidator {

    const val MAX_TITLE_CHARS = 120
    const val MAX_MESSAGE_CHARS = 4_000
    const val MAX_CHOICE_LABEL_CHARS = 160
    const val MAX_CHOICE_DESCRIPTION_CHARS = 400
    const val MAX_CHOICES = 20
    const val MAX_TEXT_INPUT_CHARS = 8_000
    const val MAX_FOLLOW_UPS = 6
    const val MAX_ACTIONS = 8
    const val MAX_SUPPLEMENTS = 8
    const val MAX_SUPPLEMENT_LABEL_CHARS = 80
    const val MAX_SUPPLEMENT_VALUE_CHARS = 500

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reasons: List<String>) : Result
    }

    /**
     * [presentationChoices] decides whether a structured presentation's `ChoiceGroup` may resolve the
     * input request it names. The default refuses every choice node: a generated surface can never
     * invent execution capability, so a caller that does not supply durable request knowledge gets
     * the strict behaviour.
     */
    fun validate(
        surface: InteractionSurface,
        presentationChoices: PresentationValidator.ChoiceBindingResolver = PresentationValidator.NO_CHOICES,
    ): Result {
        val reasons = mutableListOf<String>()

        if (surface.id.value.isBlank()) reasons += "surface id must not be blank"
        if (surface.schemaVersion > CURRENT_SURFACE_SCHEMA_VERSION) {
            reasons += "unknown surface schema version ${surface.schemaVersion}"
        }
        if (surface.schemaVersion < 1) reasons += "surface schema version must be positive"

        surface.title?.let { if (it.length > MAX_TITLE_CHARS) reasons += "title exceeds $MAX_TITLE_CHARS characters" }
        surface.message?.let { if (it.length > MAX_MESSAGE_CHARS) reasons += "message exceeds $MAX_MESSAGE_CHARS characters" }

        if (surface.supplements.size > MAX_SUPPLEMENTS) reasons += "too many supplements (max $MAX_SUPPLEMENTS)"
        surface.supplements.forEach { supplement ->
            when (supplement) {
                is InteractionSupplement.Fact -> {
                    if (supplement.label.isBlank() || supplement.label.length > MAX_SUPPLEMENT_LABEL_CHARS) {
                        reasons += "supplement fact label must contain 1-$MAX_SUPPLEMENT_LABEL_CHARS characters"
                    }
                    if (supplement.value.length > MAX_SUPPLEMENT_VALUE_CHARS) reasons += "supplement fact value is too long"
                }
                is InteractionSupplement.Status -> {
                    if (supplement.label.isBlank() || supplement.label.length > MAX_SUPPLEMENT_LABEL_CHARS) {
                        reasons += "supplement status label must contain 1-$MAX_SUPPLEMENT_LABEL_CHARS characters"
                    }
                }
                is InteractionSupplement.Notice -> {
                    if (supplement.text.isBlank() || supplement.text.length > MAX_SUPPLEMENT_VALUE_CHARS) {
                        reasons += "supplement notice must contain 1-$MAX_SUPPLEMENT_VALUE_CHARS characters"
                    }
                }
            }
        }

        if (surface.actions.size > MAX_ACTIONS) reasons += "too many actions (max $MAX_ACTIONS)"
        val actionIds = surface.actions.map { it.id.value }
        if (actionIds.size != actionIds.toSet().size) reasons += "action ids must be unique"
        surface.actions.forEach { action ->
            if (action.id.value.isBlank()) reasons += "action id must not be blank"
            if (action.label.length > MAX_TITLE_CHARS) reasons += "action label exceeds $MAX_TITLE_CHARS characters"
        }
        if (surface.kind == InteractionSurfaceKind.CONFIRMATION) {
            val semantics = surface.actions.map { it.semantic }
            if (InteractionActionSemantic.CONFIRM !in semantics) reasons += "confirmation surface requires a CONFIRM action"
            if (InteractionActionSemantic.DENY !in semantics) reasons += "confirmation surface requires a DENY action"
        }

        surface.expiresAt?.let { if (!it.isAfter(surface.createdAt)) reasons += "expiry must be after creation" }

        // Kind/content structural agreement.
        when (surface.kind) {
            InteractionSurfaceKind.CONFIRMATION ->
                if (surface.content !is InteractionContent.Confirmation) reasons += "CONFIRMATION kind requires Confirmation content"
            InteractionSurfaceKind.CHOICE -> {
                val choice = surface.content as? InteractionContent.Choice
                if (choice == null) {
                    reasons += "CHOICE kind requires Choice content"
                } else {
                    validateChoice(choice, reasons)
                }
            }
            InteractionSurfaceKind.CLARIFICATION -> when (surface.content) {
                is InteractionContent.Choice -> validateChoice(surface.content, reasons)
                is InteractionContent.TextInput -> validateTextInput(surface.content, reasons)
                is InteractionContent.TemporalInput -> validateTemporal(surface.content, reasons)
                is InteractionContent.FileSelection -> validateFileSelection(surface.content, reasons)
                else -> reasons += "CLARIFICATION kind requires a clarification content type"
            }
            InteractionSurfaceKind.TEXT_INPUT -> {
                val text = surface.content as? InteractionContent.TextInput
                if (text == null) reasons += "TEXT_INPUT kind requires TextInput content" else validateTextInput(text, reasons)
            }
            InteractionSurfaceKind.TEMPORAL_INPUT -> {
                val temporal = surface.content as? InteractionContent.TemporalInput
                if (temporal == null) reasons += "TEMPORAL_INPUT kind requires TemporalInput content" else validateTemporal(temporal, reasons)
            }
            InteractionSurfaceKind.FILE_SELECTION -> {
                val files = surface.content as? InteractionContent.FileSelection
                if (files == null) reasons += "FILE_SELECTION kind requires FileSelection content" else validateFileSelection(files, reasons)
            }
            InteractionSurfaceKind.PROGRESS -> {
                if (surface.content !is InteractionContent.Progress) reasons += "PROGRESS kind requires Progress content"
                if (surface.runId == null) reasons += "PROGRESS surface requires a run binding"
                if (surface.inputRequestId != null) reasons += "PROGRESS surface must not bind an input request"
            }
            InteractionSurfaceKind.RESULT -> {
                if (surface.content !is InteractionContent.Result) reasons += "RESULT kind requires Result content"
                if (surface.runId == null) reasons += "RESULT surface requires a run binding"
                val followUps = (surface.content as? InteractionContent.Result)?.followUps.orEmpty()
                if (followUps.size > MAX_FOLLOW_UPS) reasons += "too many follow-ups (max $MAX_FOLLOW_UPS)"
            }
            InteractionSurfaceKind.ERROR -> {
                if (surface.content !is InteractionContent.Error) reasons += "ERROR kind requires Error content"
                val followUps = (surface.content as? InteractionContent.Error)?.followUps.orEmpty()
                if (followUps.size > MAX_FOLLOW_UPS) reasons += "too many follow-ups (max $MAX_FOLLOW_UPS)"
            }
            InteractionSurfaceKind.PRESENTATION -> {
                val presentation = surface.content as? InteractionContent.Presentation
                if (presentation == null) {
                    reasons += "PRESENTATION kind requires Presentation content"
                } else {
                    if (presentation.textFallback.isBlank()) {
                        reasons += "presentation requires a text fallback"
                    } else if (presentation.textFallback.length > MAX_MESSAGE_CHARS) {
                        reasons += "presentation text fallback exceeds $MAX_MESSAGE_CHARS characters"
                    }
                    when (val result = PresentationValidator.validate(presentation.presentation, presentationChoices)) {
                        is PresentationValidator.Result.Valid -> Unit
                        is PresentationValidator.Result.Invalid -> reasons += result.reasons
                    }
                }
            }
        }

        // Input-required surfaces must be bound to their durable request.
        val inputKinds = setOf(
            InteractionSurfaceKind.CONFIRMATION,
            InteractionSurfaceKind.CLARIFICATION,
            InteractionSurfaceKind.CHOICE,
            InteractionSurfaceKind.TEXT_INPUT,
            InteractionSurfaceKind.TEMPORAL_INPUT,
            InteractionSurfaceKind.FILE_SELECTION,
        )
        if (surface.kind in inputKinds && surface.inputRequestId == null) {
            reasons += "input-required surface must reference its durable input request"
        }

        return if (reasons.isEmpty()) Result.Valid else Result.Invalid(reasons)
    }

    private fun validateChoice(choice: InteractionContent.Choice, reasons: MutableList<String>) {
        if (choice.options.isEmpty()) reasons += "choice surface requires at least one option"
        if (choice.options.size > MAX_CHOICES) reasons += "too many choices (max $MAX_CHOICES)"
        if (choice.prompt.length > MAX_MESSAGE_CHARS) reasons += "choice prompt exceeds $MAX_MESSAGE_CHARS characters"
        val ids = choice.options.map { it.id }
        if (ids.size != ids.toSet().size) reasons += "choice option ids must be unique"
        choice.options.forEach { option ->
            if (option.id.isBlank()) reasons += "choice option id must not be blank"
            if (option.label.length > MAX_CHOICE_LABEL_CHARS) reasons += "choice label exceeds $MAX_CHOICE_LABEL_CHARS characters"
            option.description?.let {
                if (it.length > MAX_CHOICE_DESCRIPTION_CHARS) reasons += "choice description exceeds $MAX_CHOICE_DESCRIPTION_CHARS characters"
            }
        }
    }

    private fun validateTextInput(text: InteractionContent.TextInput, reasons: MutableList<String>) {
        val c = text.constraints
        if (c.maxLength > MAX_TEXT_INPUT_CHARS) reasons += "text input maxLength exceeds $MAX_TEXT_INPUT_CHARS"
        if (c.maxLength < c.minLength) reasons += "text input maxLength must not be below minLength"
        if (c.minLength < 0) reasons += "text input minLength must not be negative"
        if (text.prompt.length > MAX_MESSAGE_CHARS) reasons += "text prompt exceeds $MAX_MESSAGE_CHARS characters"
        text.initialValue?.let { if (it.length > c.maxLength) reasons += "text initialValue exceeds maxLength" }
    }

    private fun validateTemporal(temporal: InteractionContent.TemporalInput, reasons: MutableList<String>) {
        if (temporal.prompt.length > MAX_MESSAGE_CHARS) reasons += "temporal prompt exceeds $MAX_MESSAGE_CHARS characters"
        if (temporal.initial is TemporalInputValue.LocalDateValue && temporal.mode == TemporalInputMode.TIME) {
            reasons += "temporal mode TIME requires a time value"
        }
        if (temporal.initial is TemporalInputValue.LocalTimeValue && temporal.mode == TemporalInputMode.DATE) {
            reasons += "temporal mode DATE requires a date value"
        }
    }

    private fun validateFileSelection(files: InteractionContent.FileSelection, reasons: MutableList<String>) {
        if (files.acceptedKinds.isEmpty()) reasons += "file selection requires at least one accepted kind"
        if (files.prompt.length > MAX_MESSAGE_CHARS) reasons += "file selection prompt exceeds $MAX_MESSAGE_CHARS characters"
    }
}
