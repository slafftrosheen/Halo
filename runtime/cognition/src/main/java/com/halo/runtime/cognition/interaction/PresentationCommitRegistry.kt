package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 committed-presentation bridge.
 *
 * Presentation is a VIEW. The durable truth stays in the AgentRun journal (a `halo_present` call that
 * really executed) and in the committed conversation turn. This registry is the only piece of
 * process-local state involved, and it exists for exactly one reason: a validated presentation
 * produced by a tool call must be able to travel with the assistant response that is committed a few
 * moments later, without inventing a second durable surface store (Part G).
 *
 * Lifetime rules, all bounded:
 *
 * - [stage] holds a validated presentation against its surface id until the runtime observes the
 *   corresponding tool call succeed. A presentation the runtime did not see succeed is never
 *   promoted, so a rejected tool call cannot leave a card behind.
 * - [observeToolSuccess] moves a staged presentation into run scope. It reads only the
 *   machine-readable `surface_id=` key from Halo's own tool-result envelope.
 * - [consume] hands the promoted presentation to the response committer once and forgets it, so a run
 *   that never commits cannot leak its card into a later conversation.
 * - entries expire ([ttlMs]) and both maps are bounded ([maxEntries]).
 */
class PresentationCommitRegistry(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = MAX_ENTRIES,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class RecordedPresentation(
        val presentation: StructuredPresentation,
        val template: String?,
        val nodeCount: Int,
    )

    private data class Entry(val recorded: RecordedPresentation, val atMs: Long)

    private val staged = LinkedHashMap<String, Entry>()
    private val promoted = LinkedHashMap<String, Entry>()

    /**
     * Remembers a presentation that already passed [PresentationValidator]. [nodeCount] comes from the
     * validator verdict, so nothing is re-counted or re-derived here.
     */
    @Synchronized
    fun stage(
        surfaceId: String,
        presentation: StructuredPresentation,
        template: String?,
        nodeCount: Int,
    ): Boolean {
        if (surfaceId.isBlank()) return false
        evict()
        staged[surfaceId] = Entry(RecordedPresentation(presentation, template, nodeCount), now())
        trim(staged)
        return true
    }

    /**
     * Called by the runtime after a tool call it observed succeeded. Returns true only when a staged
     * presentation was promoted for this run.
     */
    @Synchronized
    fun observeToolSuccess(
        runId: String?,
        interactionId: String,
        toolId: String,
        output: String,
    ): Boolean {
        if (!isPresentationTool(toolId)) return false
        evict()
        val surfaceId = surfaceIdOf(output) ?: return false
        val entry = staged.remove(surfaceId) ?: return false
        promoted[runKey(runId, interactionId)] = entry
        trim(promoted)
        return true
    }

    /** Hands the promoted presentation to the committer once, then forgets it. */
    @Synchronized
    fun consume(runId: String?, interactionId: String): RecordedPresentation? {
        evict()
        return promoted.remove(runKey(runId, interactionId))?.recorded
    }

    /** Drops run scope without returning anything (cancelled or handed-off runs). */
    @Synchronized
    fun discard(runId: String?, interactionId: String) {
        promoted.remove(runKey(runId, interactionId))
    }

    @Synchronized
    fun clear() {
        staged.clear()
        promoted.clear()
    }

    private fun evict() {
        val cutoff = now() - ttlMs
        staged.entries.removeAll { it.value.atMs < cutoff }
        promoted.entries.removeAll { it.value.atMs < cutoff }
    }

    private fun trim(map: LinkedHashMap<String, Entry>) {
        while (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: return
            map.remove(oldest)
        }
    }

    companion object {
        /** Halo's only presentation producer. The runtime does not guess at other tool ids. */
        const val PRESENTATION_TOOL_ID = "halo_present"

        /** Machine-readable key in the bounded `halo_present` result envelope. */
        const val SURFACE_ID_KEY = "surface_id="

        private const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 8

        fun isPresentationTool(toolId: String): Boolean = toolId == PRESENTATION_TOOL_ID

        fun surfaceIdOf(output: String): String? = output.lineSequence()
            .firstOrNull { it.startsWith(SURFACE_ID_KEY) }
            ?.removePrefix(SURFACE_ID_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 120 }

        /** Run scope when the journal has one, interaction scope otherwise. */
        fun runKey(runId: String?, interactionId: String): String =
            runId?.takeIf { it.isNotBlank() }?.let { "run:$it" } ?: "interaction:$interactionId"
    }
}
