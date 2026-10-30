package com.halo.app.ui.screens.notebook

import java.util.UUID
import com.halo.app.data.notebook.NotebookOrganizationChange
import com.halo.app.data.notebook.NotebookOrganizationReceipt
import com.halo.app.data.notebook.NotebookOrganizationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Main-thread-only lease of the actual Library VM. No callbacks or persistence are owned here. */
class NotebookLibrarySession(private val allowed: () -> Boolean) {
    class Lease internal constructor(val entryId: String)
    private var lease: Lease? = null
    private var model: NotebookViewModel? = null
    private var previous: NotebookLibraryView? = null
    private var revision = UUID.randomUUID().toString()
    private var organizationTicket: NotebookLibraryOrganization? = null
    private var batchTicket: NotebookLibraryBatch? = null

    class ReadbackLease internal constructor(internal val entry: Lease, internal val revision: Long)
    fun readbackLease(expectedOwnerRevision: Long? = null): ReadbackLease? = lease?.takeIf { allowed() }?.let { entry ->
        model?.libraryView()?.takeIf { expectedOwnerRevision == null || it.ownerRevision == expectedOwnerRevision }
            ?.let { ReadbackLease(entry, it.ownerRevision) }
    }
    fun canReadback(expected: ReadbackLease?): Boolean = expected != null && lease === expected.entry &&
        allowed() && model?.libraryView()?.ownerRevision == expected.revision

    fun attach(entryId: String, model: NotebookViewModel): Lease {
        check(entryId.isNotBlank())
        return Lease(entryId).also { lease = it; this.model = model; invalidate() }
    }
    fun detach(expected: Lease) {
        if (lease !== expected) return
        lease = null; model = null; invalidate()
    }
    private fun invalidate() { previous = null; revision = UUID.randomUUID().toString(); organizationTicket = null; batchTicket = null }
    fun read(expectedSnapshot: String? = null): NotebookLibraryFrame? {
        if (!allowed() || lease == null) return null
        val view = model?.libraryView() ?: return null
        if (!view.rendered.isLoaded) return null
        if (previous != view) { revision = UUID.randomUUID().toString(); previous = view }
        if (expectedSnapshot != null && expectedSnapshot != revision) return null
        return NotebookLibraryFrame(revision, view)
    }
    fun set(expectedSnapshot: String, change: NotebookLibraryChange): NotebookLibraryFrame? {
        val before = read(expectedSnapshot) ?: return null
        if (!before.view.settled || before.view.mutating) return null
        val owner = model ?: return null
        if (!owner.applyLibraryChange(change)) return null
        // Even a no-op command consumes its admission; repeated calls cannot retarget a later view.
        invalidate()
        return read()
    }

    suspend fun organization(expectedSnapshot: String, objectId: String?, collectionId: String?): NotebookLibraryOrganization? {
        val frame = read(expectedSnapshot) ?: return null
        if (!frame.view.settled || frame.view.mutating) return null
        if (objectId != null && frame.view.rendered.items.none { it.objectRef.id.value == objectId && !objectId.startsWith("learning:") }) return null
        if (collectionId != null && frame.view.rendered.collections.none { it.id == collectionId }) return null
        val owner = model ?: return null
        val data = owner.libraryOrganizationSnapshot(objectId, collectionId)
        if (read(expectedSnapshot) == null || (objectId != null && data.objectRow == null)) return null
        return NotebookLibraryOrganization(UUID.randomUUID().toString(), expectedSnapshot, data).also { organizationTicket = it }
    }

    suspend fun organize(expectedSnapshot: String, expectedOrganization: String, change: NotebookOrganizationChange, objectId: String? = null, collectionId: String? = null): NotebookOrganizationReceipt? {
        val ticket = organizationTicket?.takeIf { it.token == expectedOrganization && it.viewSnapshot == expectedSnapshot } ?: return null
        if (change is NotebookOrganizationChange.Favorite && (objectId == null || ticket.data.objectId != objectId)) return null
        if (change is NotebookOrganizationChange.Membership && (objectId == null || collectionId == null || ticket.data.objectId != objectId || ticket.data.collectionId != collectionId)) return null
        if (change == NotebookOrganizationChange.DeleteCollection && (collectionId == null || ticket.data.collectionId != collectionId)) return null
        val frame = read(expectedSnapshot) ?: return null
        if (!frame.view.settled || frame.view.mutating) return null
        val owner = model ?: return null
        val expectedLease = lease
        organizationTicket = null // Consume once, including denied/failed attempts; never replay a write.
        return owner.applyLibraryOrganization(ticket.data, change) {
            withContext(Dispatchers.Main.immediate) {
                lease === expectedLease && model === owner && read(expectedSnapshot)?.view == frame.view
            }
        }
    }

    suspend fun prepareBatch(expectedSnapshot: String, action: NotebookBatchAction, collectionId: String? = null,
        undo: Boolean = false): NotebookLibraryBatch? {
        val frame = read(expectedSnapshot) ?: return null
        if (!frame.view.settled || frame.view.mutating) return null
        val owner = model ?: return null
        val expectedLease = lease
        val admission = owner.prepareLibraryBatch(action, collectionId, undo) ?: return null
        if (lease !== expectedLease || model !== owner || read(expectedSnapshot)?.view != frame.view) return null
        return NotebookLibraryBatch(UUID.randomUUID().toString(), expectedSnapshot, admission).also { batchTicket = it }
    }

    suspend fun applyBatch(expectedSnapshot: String, expectedBatch: String, action: NotebookBatchAction,
        undo: Boolean = false): NotebookBatchReceipt? {
        // Admission is consumed even if the source/view subsequently fails validation.
        val ticket = batchTicket ?: return null
        batchTicket = null
        if (ticket.token != expectedBatch || ticket.viewSnapshot != expectedSnapshot ||
            ticket.admission.action != action || ticket.admission.undo != undo) return null
        val frame = read(expectedSnapshot) ?: return null
        if (!frame.view.settled || frame.view.mutating) return null
        val owner = model ?: return null
        val expectedLease = lease
        return owner.applyLibraryBatch(ticket.admission) {
            withContext(Dispatchers.Main.immediate) {
                // Own committed rows can disappear from the rendered list during a mixed batch.
                // Entry, authority and the exact user's view/selection revision must stay fixed.
                lease === expectedLease && model === owner && allowed() &&
                    owner.libraryView().ownerRevision == frame.view.ownerRevision
            }
        }
    }
}

data class NotebookLibraryBatch(val token: String, val viewSnapshot: String, val admission: NotebookBatchAdmission)

data class NotebookLibraryOrganization(val token: String, val viewSnapshot: String, val data: NotebookOrganizationSnapshot)

data class NotebookLibraryView(
    val rendered: NotebookUiState,
    val query: String,
    val filter: NotebookFilter,
    val sort: NotebookSort,
    val collectionId: String?,
    val selection: Set<String>,
    val mutating: Boolean,
    val ownerRevision: Long,
) {
    val settled: Boolean get() = rendered.isLoaded && !rendered.loadError && rendered.resolvedQuery == query.trim() &&
        rendered.query == query && rendered.filter == filter && rendered.sort == sort &&
        rendered.selectedCollectionId == collectionId
}
data class NotebookLibraryFrame(val snapshot: String, val view: NotebookLibraryView)
sealed interface NotebookLibraryChange {
    data class Query(val text: String) : NotebookLibraryChange
    data class Filter(val value: NotebookFilter) : NotebookLibraryChange
    data class Sort(val value: NotebookSort) : NotebookLibraryChange
    data class Collection(val id: String?) : NotebookLibraryChange
    data class Selection(val ids: Set<String>) : NotebookLibraryChange
}
