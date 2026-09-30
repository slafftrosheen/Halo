package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 response commit contract.
 *
 * A terminal assistant response may now carry text **and** an optional validated structured
 * presentation. Text remains required: it is the TTS input, the voice-only fallback, the Notebook
 * text fallback, and the only thing that ever goes back into model context.
 *
 * The presentation is a VIEW. It is never spoken, never fed back into context, and never grants
 * authority.
 */
data class AssistantPresentation(
    val text: String,
    val structured: StructuredPresentation? = null,
    val provenance: PresentationProvenance? = null,
) {
    /** What speech and text-only hosts receive. Never a serialized UI tree. */
    fun spokenText(): String = text.trim()

    fun hasStructured(): Boolean = structured != null

    companion object {
        fun textOnly(text: String): AssistantPresentation = AssistantPresentation(text = text)
    }
}

/**
 * Update/delete semantics for projected surfaces (Part V).
 *
 * Harvested conceptually from A2UI's CreateSurface/UpdateComponents/DeleteSurface, mapped into Halo:
 * these are **projection events**, not execution truth, and they carry a monotonic revision so a
 * stale update cannot overwrite newer state (progress, plan status, device status).
 */
data class PresentationSurfaceRevision(
    val surfaceId: String,
    val revision: Long,
    val updatedAtMs: Long,
)

sealed interface PresentationProjectionEvent {
    val revision: PresentationSurfaceRevision

    data class SurfaceCreated(
        override val revision: PresentationSurfaceRevision,
        val presentation: StructuredPresentation,
    ) : PresentationProjectionEvent

    data class SurfaceUpdated(
        override val revision: PresentationSurfaceRevision,
        val presentation: StructuredPresentation,
    ) : PresentationProjectionEvent

    data class SurfaceRemoved(
        override val revision: PresentationSurfaceRevision,
    ) : PresentationProjectionEvent
}

/**
 * In-memory projection registry. Deliberately not durable: surfaces are rebuilt from conversation or
 * journal truth after process death, so there is no second surface database.
 */
class PresentationSurfaceProjection(
    private val maxActiveSurfaces: Int = MAX_ACTIVE_SURFACES,
) {
    private data class Entry(val revision: Long, val presentation: StructuredPresentation?, val updatedAtMs: Long)

    private val surfaces = LinkedHashMap<String, Entry>()

    /** Returns false when the incoming revision is stale or equal — never overwrites newer state. */
    @Synchronized
    fun apply(event: PresentationProjectionEvent): Boolean {
        val id = event.revision.surfaceId
        if (id.isBlank()) return false
        val current = surfaces[id]
        if (current != null && event.revision.revision <= current.revision) return false
        when (event) {
            is PresentationProjectionEvent.SurfaceRemoved -> surfaces.remove(id)
            is PresentationProjectionEvent.SurfaceCreated,
            is PresentationProjectionEvent.SurfaceUpdated,
            -> {
                val presentation = when (event) {
                    is PresentationProjectionEvent.SurfaceCreated -> event.presentation
                    is PresentationProjectionEvent.SurfaceUpdated -> event.presentation
                    else -> return false
                }
                while (surfaces.size >= maxActiveSurfaces && surfaces.keys.firstOrNull() != id) {
                    val oldest = surfaces.keys.firstOrNull() ?: break
                    surfaces.remove(oldest)
                }
                surfaces[id] = Entry(event.revision.revision, presentation, event.revision.updatedAtMs)
            }
        }
        return true
    }

    @Synchronized
    fun presentation(surfaceId: String): StructuredPresentation? = surfaces[surfaceId]?.presentation

    @Synchronized
    fun revisionOf(surfaceId: String): Long? = surfaces[surfaceId]?.revision

    @Synchronized
    fun activeIds(): List<String> = surfaces.keys.toList()

    @Synchronized
    fun activeCount(): Int = surfaces.size

    @Synchronized
    fun clear() = surfaces.clear()

    /** Convenience for progress/status producers that always bump by one. */
    fun nextRevision(surfaceId: String, nowMs: Long): PresentationSurfaceRevision =
        PresentationSurfaceRevision(surfaceId, (revisionOf(surfaceId) ?: 0L) + 1L, nowMs)

    companion object {
        const val MAX_ACTIVE_SURFACES = 8
    }
}
