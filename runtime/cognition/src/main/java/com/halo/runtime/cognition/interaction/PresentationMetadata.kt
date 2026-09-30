package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 committed-response presentation metadata.
 *
 * A terminal assistant response keeps its **text** as the semantic truth. When it also produced a
 * validated structured presentation, the payload rides alongside in committed turn metadata so Home
 * can rebuild the card after process death without a second surface database.
 *
 * Rules encoded here:
 *
 * - size is capped before anything is persisted ([PresentationLimits.MAX_PAYLOAD_CHARS]);
 * - decode failure returns null, and the caller shows prose (never a crash, never a half-tree);
 * - the payload is **never** fed back into model context (see [contextSafeText]).
 */
object PresentationMetadata {

    /** Schema version of the stored payload. */
    const val KEY_SCHEMA_VERSION = "presentationSchemaVersion"

    /** Encoded presentation, bounded. Absent for prose-only responses. */
    const val KEY_PAYLOAD = "presentationPayload"

    /** Content-free summary of what was presented, for analytics/debug without the tree. */
    const val KEY_SUMMARY = "presentationSummary"

    /** Content-free provenance keys for a committed structured response (Part F). */
    const val KEY_SOURCE_RUN_ID = "presentationRunId"
    const val KEY_SOURCE_INTERACTION_ID = "presentationInteractionId"
    const val KEY_PROVIDER_ID = "presentationProviderId"
    const val KEY_MODEL_ID = "presentationModelId"
    const val KEY_CREATED_AT = "presentationCreatedAt"

    /** Compact, non-rendered metadata: template name and node count only. */
    fun summaryOf(template: String?, nodeCount: Int): String =
        "template=${template ?: "none"} nodes=$nodeCount"

    /**
     * Part F provenance, kept in bounded machine keys rather than a nested object.
     *
     * All values are identifiers or a timestamp — never prompt text, generated text or tool
     * arguments. Normal users never see these; they exist so expanded Activity/debug detail can say
     * which provider/model produced a card without a second store.
     */
    fun provenanceMetadata(
        runId: String?,
        interactionId: String?,
        providerId: String?,
        modelId: String?,
        createdAtMs: Long,
    ): Map<String, String> = buildMap {
        runId?.takeIf { it.isNotBlank() }?.let { put(KEY_SOURCE_RUN_ID, it.take(80)) }
        interactionId?.takeIf { it.isNotBlank() }?.let { put(KEY_SOURCE_INTERACTION_ID, it.take(80)) }
        providerId?.takeIf { it.isNotBlank() }?.let { put(KEY_PROVIDER_ID, it.take(80)) }
        modelId?.takeIf { it.isNotBlank() }?.let { put(KEY_MODEL_ID, it.take(120)) }
        put(KEY_CREATED_AT, createdAtMs.toString())
    }

    /**
     * Persists only a payload the validator accepted and that fits the cap.
     * Returns an empty map for prose-only responses.
     */
    fun metadataFor(
        presentation: StructuredPresentation?,
        template: String? = null,
        nodeCount: Int? = null,
    ): Map<String, String> {
        if (presentation == null) return emptyMap()
        val verdict = PresentationValidator.validate(presentation)
        if (verdict is PresentationValidator.Result.Invalid) return emptyMap()
        val encoded = PresentationCodec.encode(presentation)
        if (encoded.length > PresentationLimits.MAX_PAYLOAD_CHARS) return emptyMap()
        return mapOf(
            KEY_SCHEMA_VERSION to presentation.schemaVersion.toString(),
            KEY_PAYLOAD to encoded,
            KEY_SUMMARY to summaryOf(
                template = template,
                nodeCount = nodeCount ?: (verdict as PresentationValidator.Result.Valid).nodeCount,
            ),
        )
    }

    /** Rebuilds a committed presentation. Null means "render the text". */
    fun decode(metadata: Map<String, String>): StructuredPresentation? {
        val version = metadata[KEY_SCHEMA_VERSION]?.toIntOrNull() ?: return null
        if (version !in 1..CURRENT_PRESENTATION_SCHEMA_VERSION) return null
        val presentation = PresentationCodec.decode(metadata[KEY_PAYLOAD]) ?: return null
        return presentation.takeIf { PresentationValidator.validate(it) is PresentationValidator.Result.Valid }
    }

    /**
     * What the context assembler may use. Deliberately ignores the presentation payload: a serialized
     * component tree would waste a small local model's context for no semantic gain.
     */
    fun contextSafeText(assistantText: String, metadata: Map<String, String>): String {
        metadata[KEY_PAYLOAD] // read for documentation value only
        return assistantText
    }
}
