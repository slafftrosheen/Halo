package com.halo.app.context

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.WorkspaceActionIds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** Ephemeral UI metadata only. No card text, saved state, domain writes or execution callbacks. */
data class CanvasContextProjection(val canvasId: String, val selectedNodeIds: Set<String>,
    val visibleNodeIds: Set<String>, val viewIdentity: Any)

data class CurrentCanvasContextSnapshot(val canvasId: String, val version: String,
    val selectedNodeIds: List<String>, val selectionToken: String)

/** Attached-view projection over the existing canonical Canvas tables, analogous to Studio's active-project owner. */
class CurrentCanvasContextOwner(private val database: AppDatabase) {
    private data class Binding(val token: String, val project: suspend () -> CanvasContextProjection?)
    private data class Source(val version: String, val nodeIds: Set<String>)
    private data class Observed(val binding: Binding, val projection: CanvasContextProjection, val snapshot: CurrentCanvasContextSnapshot)
    private val lock = Any()
    private val bindings = linkedMapOf<String, Binding>()
    private var observed: Observed? = null

    fun attach(snapshot: suspend () -> CanvasContextProjection?): String = synchronized(lock) {
        UUID.randomUUID().toString().also { token -> bindings[token] = Binding(token, snapshot); observed = null }
    }
    fun detach(token: String) = synchronized(lock) { if (bindings.remove(token) != null) observed = null; Unit }
    private fun binding(): Binding = synchronized(lock) {
        check(bindings.size == 1) { "Open one Canvas view before asking about this board." }
        bindings.values.single()
    }
    private suspend fun readSource(canvas: String): Source = database.withTransaction {
        require(WorkspaceActionIds.isValid(canvas))
        val header = requireNotNull(database.notebookDao().getWorkspaceObject(canvas))
        check(header.lifecycle == "ACTIVE" && (header.type == "CANVAS" || header.contentRef == "canvas:$canvas")) { "Canvas unavailable" }
        val document = requireNotNull(database.notebookCanvasDao().getDocument(canvas))
        Source("canvas:${document.revision}", database.notebookCanvasDao().getNodes(canvas).map { it.id }.toSet())
    }
    private fun sameView(before: CanvasContextProjection, after: CanvasContextProjection): Boolean =
        before.viewIdentity === after.viewIdentity && before.canvasId == after.canvasId &&
            before.selectedNodeIds == after.selectedNodeIds && before.visibleNodeIds == after.visibleNodeIds

    suspend fun current(): CurrentCanvasContextSnapshot {
        val binding = binding()
        val projection = requireNotNull(binding.project()) { "Canvas has pending edits or is unavailable. Finish the edit first." }
        check(projection.selectedNodeIds.size <= 100 && projection.selectedNodeIds.all { it in projection.visibleNodeIds }) { "Selected cards are unavailable" }
        val source = readSource(projection.canvasId)
        check(projection.visibleNodeIds == source.nodeIds && projection.selectedNodeIds.all { it in source.nodeIds }) { "Canvas view is stale. Wait for it to refresh." }
        val again = requireNotNull(binding.project()) { "Canvas view changed" }
        check(sameView(projection, again) && binding() === binding) { "Canvas view or selection changed. Read it again." }
        check(readSource(projection.canvasId) == source) { "Canvas changed. Read it again." }
        check(binding.project()?.let { sameView(projection, it) } == true) { "Canvas view changed during source validation" }
        currentCoroutineContext().ensureActive()
        return synchronized(lock) {
            check(bindings.size == 1 && bindings[binding.token] === binding) { "Canvas view closed" }
            val previous = observed
            if (previous != null && previous.binding === binding && sameView(previous.projection, projection) && previous.snapshot.version == source.version)
                previous.snapshot
            else CurrentCanvasContextSnapshot(projection.canvasId, source.version, projection.selectedNodeIds.sorted(), UUID.randomUUID().toString()).also {
                observed = Observed(binding, projection, it)
            }
        }
    }

    /** Check before canonical writes. Bulk selections require every selected card, never inferred members. */
    suspend fun requireSelection(token: String, canvasId: String, version: String, targets: Set<String>, exactSelection: Boolean = true) {
        val value = synchronized(lock) { requireNotNull(observed) { "Read the active Canvas first" } }
        check(value.snapshot.selectionToken == token && value.snapshot.canvasId == canvasId && value.snapshot.version == version) { "Canvas selection token expired" }
        check(targets.isNotEmpty() && targets.all { it in value.snapshot.selectedNodeIds } &&
            (!exactSelection || targets == value.snapshot.selectedNodeIds.toSet())) { "Selected card IDs changed. Clarify the selection." }
        requireUnchangedSelection(token)
        val source = readSource(canvasId)
        check(source.version == version && targets.all { it in source.nodeIds }) { "Canvas source changed" }
        requireUnchangedSelection(token)
    }

    /** Rechecked before transaction commit; a change causes the existing Room transaction to roll back. */
    suspend fun requireUnchangedSelection(token: String) {
        val value = synchronized(lock) { requireNotNull(observed) { "Canvas selection unavailable" } }
        check(value.snapshot.selectionToken == token && binding() === value.binding) { "Canvas view closed or ambiguous" }
        check(value.binding.project()?.let { sameView(value.projection, it) } == true) { "Canvas selection changed" }
        currentCoroutineContext().ensureActive()
        check(synchronized(lock) { observed === value && bindings.size == 1 && bindings[value.binding.token] === value.binding }) { "Canvas selection expired" }
    }
}
