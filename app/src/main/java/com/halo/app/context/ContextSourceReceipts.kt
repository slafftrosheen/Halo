package com.halo.app.context

import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.context.ContextReference
import com.halo.runtime.cognition.context.WorkspaceContextSource

enum class SourceReceiptStatus { UNCHANGED, CHANGED, UNKNOWN, UNAVAILABLE }
data class ContextSourceReceipt(val objectId: String, val recordedVersion: String?, val currentVersion: String?,
    val title: String?, val status: SourceReceiptStatus,
    val textAnchor: com.halo.sdk.runtime.study.StudyTextAnchor? = null)
data class ContextSourceReceiptFrame(val runId: String, val recorded: Boolean,
    val sources: List<ContextSourceReceipt>, val memoryIds: List<String>, val truncated: Boolean)

/** Read-only projection of the existing run journal and canonical source metadata. No content cache or execution owner. */
class ContextSourceReceipts(private val runs: AgentRunStore, private val workspace: WorkspaceContextSource) {
    /** Manual local history inspection is not permission to export metadata to an AI target. */
    suspend fun read(runId: String): ContextSourceReceiptFrame? = project(runId, null)

    suspend fun read(runId: String, targetIsLocal: Boolean): ContextSourceReceiptFrame? = project(runId, targetIsLocal)

    /** Recheck at tool-return time too: metadata resolution may suspend while access is revoked. */
    suspend fun forTarget(frame: ContextSourceReceiptFrame, targetIsLocal: Boolean): ContextSourceReceiptFrame {
        val sources = frame.sources.filter { workspace.permits(it.objectId, targetIsLocal) }
        val memories = frame.memoryIds.filter { workspace.permits(it, targetIsLocal) }
        return frame.copy(sources = sources, memoryIds = memories,
            truncated = frame.truncated || sources.size != frame.sources.size || memories.size != frame.memoryIds.size)
    }

    private suspend fun project(runId: String, targetIsLocal: Boolean?): ContextSourceReceiptFrame? {
        require(runId.length in 1..240)
        val run = runs.get(AgentRunId(runId)) ?: return null
        if (run.id.value != runId) return null
        val receipts = run.steps.filterIsInstance<AgentStep.ContextUsed>()
        val references = receipts.flatMap { receipt ->
            val versioned = receipt.workspaceReferences.groupBy { it.objectId }
            receipt.objectIds.flatMap { id -> versioned[id] ?: listOf(ContextReference.WorkspaceObject(id, null)) }
        }.asReversed().distinct().asReversed()
        // Last use determines recency, including a source first seen much earlier.
        val memoryIds = receipts.flatMap { it.memoryIds }.asReversed().distinct().asReversed()
        val selected = references.takeLast(20).filter { targetIsLocal == null || workspace.permits(it.objectId, targetIsLocal) }
        val current = selected.map { it.objectId }.distinct().associateWith { id ->
            workspace.referenceMetadata(id)?.takeIf { it.objectId == id && it.version.length in 1..2000 }
        }
        val sources = selected.map { reference ->
            val metadata = current[reference.objectId]
            ContextSourceReceipt(reference.objectId, reference.version, metadata?.version, metadata?.title?.take(200), when {
                metadata == null -> SourceReceiptStatus.UNAVAILABLE
                reference.version == null -> SourceReceiptStatus.UNKNOWN
                reference.version == metadata.version -> SourceReceiptStatus.UNCHANGED
                else -> SourceReceiptStatus.CHANGED
            }, reference.textAnchor)
        }
        val memories = memoryIds.takeLast(12).filter { targetIsLocal == null || workspace.permits(it, targetIsLocal) }
        val frame = ContextSourceReceiptFrame(runId, receipts.isNotEmpty(), sources, memories,
            references.size > selected.size || memoryIds.size > memories.size)
        return if (targetIsLocal == null) frame else forTarget(frame, targetIsLocal)
    }
}
