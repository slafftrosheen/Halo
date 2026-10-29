package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.memory.RoomMemoryRepository
import com.halo.app.data.notebook.*
import com.halo.app.data.search.*
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import com.halo.app.data.workspace.contextOwner
import com.halo.app.di.tools.host.NotebookBatchTools
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Source regressions for real canonical Room owners. Written during the no-execution phase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookLibraryBatchTest {
    private lateinit var f: WorkspaceFileToolsFixture
    private lateinit var conversations: RoomConversationStore
    private lateinit var organization: NotebookOrganizationRepository
    private lateinit var owner: NotebookLibraryBatchOwner
    private lateinit var context: WorkspaceContextStore
    private lateinit var model: NotebookViewModel
    private val models = ViewModelStore()
    private var allowed = true
    private var revokeOnTrashReceipt = false
    private val session = NotebookLibrarySession {
        allowed && (!revokeOnTrashReceipt || !::model.isInitialized || model.trashUndoIds.value.isEmpty())
    }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        f = WorkspaceFileToolsFixture()
        conversations = RoomConversationStore(f.database, f.scope)
        conversations.awaitReady()
        organization = NotebookOrganizationRepository(f.database.notebookOrganizationDao())
        val notes = NotebookUseCases(f.notes)
        context = f.contextOwner()
        val workspace = WorkspaceUseCases(f.workspace, NoSearch, context, conversations,
            f.files, RoomMemoryRepository(f.database), notes)
        owner = NotebookLibraryBatchOwner(f.database, workspace, organization, conversations)
        model = NotebookViewModel(workspace, notes, organization, f.canvases,
            conversationStore = conversations, batchOwner = owner)
        models.put("library", model)
        f.scope.launch { model.uiState.collect {} }
        session.attach("resumed-library", model)
        Unit
    }

    @After fun close() {
        val work = if (::model.isInitialized) model.viewModelScope.coroutineContext[Job] else null
        try {
            models.clear()
            runBlocking {
                withTimeout(10_000) {
                    work?.join()
                    if (::f.isInitialized) f.scope.coroutineContext[Job]?.cancelAndJoin()
                }
            }
        } finally {
            try { if (::f.isInitialized) f.close() } finally { Dispatchers.resetMain() }
        }
    }

    private suspend fun visible(ids: Set<String> = emptySet(), filter: NotebookFilter = model.libraryView().filter) {
        withTimeout(10_000) {
            // Read after the fixture writes commit. ID presence alone can accept
            // the active chat's pre-append title/timestamp, then its Room emission
            // correctly invalidates the view while prepareBatch captures sources.
            val expected = ids.associateWith { id -> requireNotNull(f.workspace.get(WorkspaceObjectId(id))) }
            // A completed CONTEXT batch commits working_set before its Room-backed
            // UI projection catches up. Wait for that exact committed set too.
            val expectedWorkingSet = f.database.workspaceContextDao().selectedIds().toSet()
            model.uiState.first { state ->
                state.isLoaded && state.filter == filter && state.query == model.queryText.value &&
                    state.resolvedQuery == model.queryText.value.trim() &&
                    state.workingSet == expectedWorkingSet &&
                    expected.all { (id, committed) ->
                        state.items.firstOrNull { it.objectRef.id.value == id }?.objectRef == committed
                    }
            }
        }
    }
    private fun select(ids: Set<String>) { assertTrue(model.applyLibraryChange(NotebookLibraryChange.Selection(ids))) }
    private fun snapshot() = requireNotNull(session.read()).snapshot
    private suspend fun lifecycle(id: String) = requireNotNull(f.database.notebookDao().getWorkspaceObject(id)).lifecycle

    @Test fun mixedTrashHasExactSuccessfulUndoAndNeverChangesActiveChatWriter() = runBlocking {
        val note = f.notes.createNote("private note body", title = "private note title").id
        val canvas = f.canvases.create("private canvas title")
        val image = f.png("private image source").objectId.value
        conversations.append("private chat question", "private chat answer", emptyMap())
        val active = requireNotNull(conversations.activeConversationId())
        val targets = setOf(note, canvas, image, active)
        visible(targets); select(targets)
        val before = requireNotNull(session.read())
        val view = before.snapshot
        val ticket = requireNotNull(session.prepareBatch(view, NotebookBatchAction.TRASH)) {
            val after = session.read()
            "Mixed batch admission changed: present=${after != null}" +
                " snapshotUnchanged=${after?.snapshot == view}" +
                " renderedUnchanged=${after?.view?.rendered == before.view.rendered}" +
                " ownerRevisionUnchanged=${after?.view?.ownerRevision == before.view.ownerRevision}" +
                " selectionUnchanged=${after?.view?.selection == before.view.selection}" +
                " settled=${after?.view?.settled} mutating=${after?.view?.mutating}"
        }
        val receipt = requireNotNull(session.applyBatch(view, ticket.token, NotebookBatchAction.TRASH))
        assertEquals(setOf(note, canvas, image), receipt.succeeded)
        assertEquals(setOf(active), receipt.failed)
        assertTrue(receipt.unknown.isEmpty())
        assertEquals(receipt.succeeded, model.trashUndoIds.value)
        assertEquals(setOf(active), model.selectedIds.value)
        assertEquals(active, conversations.activeConversationId())
        assertEquals("ACTIVE", lifecycle(active))
        assertNull("Admission must be one-use", session.applyBatch(view, ticket.token, NotebookBatchAction.TRASH))

        // Undo deliberately does not derive targets from the current selection or visible Trash.
        model.setFilter(NotebookFilter.TRASH); visible(receipt.succeeded, NotebookFilter.TRASH)
        val undoView = snapshot()
        val undo = requireNotNull(session.prepareBatch(undoView, NotebookBatchAction.RESTORE, undo = true))
        assertEquals(receipt.succeeded, undo.admission.sources.map { it.objectId }.toSet())
        val restored = requireNotNull(session.applyBatch(undoView, undo.token, NotebookBatchAction.RESTORE, undo = true))
        assertEquals(receipt.succeeded, restored.succeeded)
        assertTrue(model.trashUndoIds.value.isEmpty())
        targets.forEach { assertEquals("ACTIVE", lifecycle(it)) }
        assertEquals(active, conversations.activeConversationId())
    }

    @Test fun changedCanonicalNoteAtSameHeaderTimestampIsPartialAndUndoDoesNotOverwriteLaterSource() = runBlocking {
        val changed = f.notes.createNote("original").id
        val unchanged = f.notes.createNote("unchanged").id
        visible(setOf(changed, unchanged)); select(setOf(changed, unchanged))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        val header = requireNotNull(f.database.notebookDao().getWorkspaceObject(changed))
        assertTrue(f.notes.saveNote(changed, "new canonical revision"))
        // Equal header and time cannot bypass the actual canonical document revision.
        f.database.notebookDao().insertWorkspaceObject(header)
        val receipt = requireNotNull(model.applyLibraryBatch(admission))
        assertEquals(setOf(unchanged), receipt.succeeded)
        assertEquals(setOf(changed), receipt.failed)
        assertEquals("ACTIVE", lifecycle(changed))
        assertEquals(setOf(unchanged), model.trashUndoIds.value)

        model.setFilter(NotebookFilter.TRASH); visible(setOf(unchanged), NotebookFilter.TRASH)
        val undo = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.RESTORE, undo = true))
        f.database.notebookDao().updateTitle(unchanged, "Changed after Trash", header.updatedAt)
        val refused = requireNotNull(model.applyLibraryBatch(undo))
        assertTrue(refused.succeeded.isEmpty())
        assertEquals(setOf(unchanged), refused.failed)
        assertEquals("TRASHED", lifecycle(unchanged))
        assertEquals(setOf(unchanged), model.trashUndoIds.value)
    }

    @Test fun selectionAndEntryChangesConsumeStaleTicketsAndCannotRetargetTheirSourceSet() = runBlocking {
        val first = f.notes.createNote("one").id
        val second = f.notes.createNote("two").id
        visible(setOf(first, second)); select(setOf(first))
        val view = snapshot()
        val ticket = requireNotNull(session.prepareBatch(view, NotebookBatchAction.ARCHIVE))
        select(setOf(second))
        assertNull(session.applyBatch(view, ticket.token, NotebookBatchAction.ARCHIVE))
        select(setOf(first))
        assertNull(session.applyBatch(view, ticket.token, NotebookBatchAction.ARCHIVE))
        val refreshed = snapshot()
        val next = requireNotNull(session.prepareBatch(refreshed, NotebookBatchAction.TRASH))
        session.attach("replacement-entry", model)
        assertNull(session.applyBatch(refreshed, next.token, NotebookBatchAction.TRASH))
        assertEquals("ACTIVE", lifecycle(first)); assertEquals("ACTIVE", lifecycle(second))
    }

    @Test fun membershipAndCollectionChangesAreGuardedWithoutRemovingOtherMembers() = runBlocking {
        val first = f.notes.createNote("one").id
        val second = f.notes.createNote("two").id
        val other = f.notes.createNote("other").id
        val collection = requireNotNull(organization.createCollection("Owned collection"))
        organization.setMembership(collection.id, other, true)
        visible(setOf(first, second)); select(setOf(first, second))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.COLLECTION, collection.id))
        organization.setMembership(collection.id, first, true)
        val receipt = requireNotNull(model.applyLibraryBatch(admission))
        assertEquals(setOf(first), receipt.failed)
        assertEquals(setOf(second), receipt.succeeded)
        assertEquals(setOf(first, second, other), organization.observeMembers(collection.id).first())
        select(setOf(second))
        val deleted = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.COLLECTION, collection.id))
        assertTrue(organization.deleteCollection(collection.id))
        assertEquals(setOf(second), requireNotNull(model.applyLibraryBatch(deleted)).failed)
        assertEquals("ACTIVE", lifecycle(second))
    }

    @Test fun contextUsesCanonicalWorkingSetWithoutGrantingCloudSharingAndTypedReadReturnsNoContent() = runBlocking {
        val note = f.notes.createNote("DO_NOT_DISCLOSE_BODY", title = "DO_NOT_DISCLOSE_TITLE").id
        visible(setOf(note)); select(setOf(note))
        val executor = NotebookBatchTools(session)
        val cloudSharing = context.cloudSharing.value
        withContext(RunToolScope(AgentRunId("local-batch"), targetIsLocal = true)) {
            val view = snapshot()
            val result = executor.execute(ToolCall("prepare", NotebookBatchTools.READ,
                mapOf("expectedSnapshot" to view, "operation" to "CONTEXT"))) as ToolExecutionResult.Success
            assertFalse(result.output.contains("DO_NOT_DISCLOSE"))
            val prepared = JSONObject(result.output)
            val call = ToolCall("apply", NotebookBatchTools.WRITE, mapOf("expectedSnapshot" to view,
                "expectedBatch" to prepared.getString("batchSnapshot"), "operation" to "CONTEXT"))
            val applied = executor.execute(call, ToolExecutionContext("approved", directUserAuthority = true))
            assertTrue(applied is ToolExecutionResult.Success)
            assertTrue(note in f.database.workspaceContextDao().selectedIds())
            assertEquals(cloudSharing, context.cloudSharing.value)
        }
    }

    @Test fun leaseRevocationDuringObjectTransactionRollsBackAndLastTrashIsInvalidatedByNextBatch() = runBlocking {
        val note = f.notes.createNote("one").id
        visible(setOf(note)); select(setOf(note))
        val denied = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        var checks = 0
        val result = requireNotNull(model.applyLibraryBatch(denied) { ++checks < 3 })
        assertEquals(setOf(note), result.failed)
        assertEquals("ACTIVE", lifecycle(note))
        val trash = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        assertEquals(setOf(note), requireNotNull(model.applyLibraryBatch(trash)).succeeded)
        model.setFilter(NotebookFilter.TRASH); visible(setOf(note), NotebookFilter.TRASH); select(setOf(note))
        val context = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.CONTEXT))
        assertEquals(setOf(note), requireNotNull(model.applyLibraryBatch(context)).failed)
        assertTrue(model.trashUndoIds.value.isEmpty())
    }

    @Test fun ownerExceptionRemainsUnconfirmedAndNeverBecomesASuccessfulTrashUndoTarget() = runBlocking {
        val note = f.notes.createNote("one").id
        visible(setOf(note)); select(setOf(note))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        var checks = 0
        val receipt = requireNotNull(model.applyLibraryBatch(admission) {
            if (++checks == 3) error("Synthetic owner/access uncertainty")
            true
        })
        assertTrue(receipt.succeeded.isEmpty())
        assertTrue(receipt.failed.isEmpty())
        assertEquals(setOf(note), receipt.unknown)
        assertTrue(model.trashUndoIds.value.isEmpty())
        assertEquals(setOf(note), model.selectedIds.value)
        assertEquals("ACTIVE", lifecycle(note)) // Room rolled back; the receipt conservatively claims no success.
    }

    @Test fun revokedDisclosureAfterCommittedTrashReturnsUnknownWithoutSourceIdentifiers() = runBlocking {
        val note = f.notes.createNote("PRIVATE_BODY", title = "PRIVATE_TITLE").id
        visible(setOf(note)); select(setOf(note))
        val executor = NotebookBatchTools(session)
        withContext(RunToolScope(AgentRunId("revoked-after-effect"), targetIsLocal = true)) {
            val view = snapshot()
            val prepared = executor.execute(ToolCall("prepare", NotebookBatchTools.READ,
                mapOf("expectedSnapshot" to view, "operation" to "TRASH"))) as ToolExecutionResult.Success
            // Revoke at the owner receipt boundary, after its transaction has committed.
            revokeOnTrashReceipt = true
            val result = executor.execute(ToolCall("apply", NotebookBatchTools.WRITE,
                mapOf("expectedSnapshot" to view, "expectedBatch" to JSONObject(prepared.output).getString("batchSnapshot"),
                    "operation" to "TRASH")), ToolExecutionContext("approved", directUserAuthority = true))
            assertTrue(result is ToolExecutionResult.Failed)
            val failure = result as ToolExecutionResult.Failed
            assertTrue(failure.completionUnknown)
            assertNull(failure.payload)
            assertFalse(failure.message.contains(note))
            assertFalse(failure.message.contains("PRIVATE_"))
            assertEquals("TRASHED", lifecycle(note))
        }
    }

    @Test fun noteRecoveryDraftKeepsItsExistingLifecycleProtectionWithinAMixedBatch() = runBlocking {
        val draft = f.notes.createNote("saved").id
        val other = f.notes.createNote("other").id
        f.notes.saveDraft(draft, "recovery content")
        visible(setOf(draft, other)); select(setOf(draft, other))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        val receipt = requireNotNull(model.applyLibraryBatch(admission))
        assertEquals(setOf(draft), receipt.failed)
        assertEquals(setOf(other), receipt.succeeded)
        assertEquals("ACTIVE", lifecycle(draft))
        assertNotNull(f.notes.getDraft(draft))
        assertEquals(setOf(other), model.trashUndoIds.value)
    }

    @Test fun savedChatCanonicalVersionAndWriterMutexCoverArchiveTrashAndRestore() = runBlocking {
        conversations.append("private question", "private answer", emptyMap())
        val saved = requireNotNull(conversations.activeConversationId())
        conversations.newConversation()
        val writer = conversations.activeConversationId()
        var source = requireNotNull(owner.capture(saved))
        for (action in listOf(NotebookBatchAction.ARCHIVE, NotebookBatchAction.RESTORE,
            NotebookBatchAction.TRASH, NotebookBatchAction.RESTORE)) {
            val previousVersion = source.version
            source = owner.apply(source, NotebookBatchAdmission(action, listOf(source))) { true }
            assertNotEquals(previousVersion, source.version)
            assertEquals(writer, conversations.activeConversationId())
        }
        val expected = source
        assertTrue(conversations.activateExistingConversation(saved))
        val writerAdmission = NotebookBatchAdmission(NotebookBatchAction.TRASH, listOf(expected))
        try {
            owner.apply(expected, writerAdmission) { true }
            fail("The active writer must be refused under the conversation owner mutex")
        } catch (_: com.halo.app.data.SavedChatAdmissionException) { }
        assertEquals("ACTIVE", lifecycle(saved))
    }

    @Test fun cancellationKeepsCommittedTrashPrefixUndoAndOnlyUnattemptedSelection() = runBlocking {
        val first = f.notes.createNote("first").id
        val second = f.notes.createNote("second").id
        visible(setOf(first, second)); select(linkedSetOf(first, second))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        var checks = 0
        try {
            model.applyLibraryBatch(admission) {
                // VM admission + first object's three transaction checks complete first;
                // cancellation enters at the next object's initial transaction admission.
                if (++checks == 5) throw CancellationException("Cancel before second source")
                true
            }
            fail("Expected cancellation at the next source boundary")
        } catch (_: CancellationException) { }
        assertEquals("TRASHED", lifecycle(first))
        assertEquals("ACTIVE", lifecycle(second))
        assertEquals(setOf(first), model.trashUndoIds.value)
        assertEquals(setOf(second), model.selectedIds.value)
        assertFalse(model.libraryView().mutating)
    }

    @Test fun staleSavedChatVersionIsKnownUnchangedRatherThanUnknownCompletion() = runBlocking {
        conversations.append("saved", "answer", emptyMap())
        val saved = requireNotNull(conversations.activeConversationId())
        conversations.newConversation()
        visible(setOf(saved)); select(setOf(saved))
        val admission = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.TRASH))
        conversations.renameConversation(saved, "Changed canonical saved chat")
        val receipt = requireNotNull(model.applyLibraryBatch(admission))
        assertEquals(setOf(saved), receipt.failed)
        assertTrue(receipt.unknown.isEmpty())
        assertTrue(receipt.succeeded.isEmpty())
        assertEquals("ACTIVE", lifecycle(saved))
        assertTrue(model.trashUndoIds.value.isEmpty())
    }

    @Test fun actualTouchCallbacksAndTypedRestoreUseTheSameMixedSelectionOwner() = runBlocking {
        val note = f.notes.createNote("note").id
        val canvas = f.canvases.create("canvas")
        val ids = setOf(note, canvas)
        visible(ids); select(ids)
        model.applyToSelection(NotebookBatchAction.ARCHIVE).join()
        ids.forEach { assertEquals("ARCHIVED", lifecycle(it)) }
        model.setFilter(NotebookFilter.ARCHIVED); visible(ids, NotebookFilter.ARCHIVED); select(ids)
        val executor = NotebookBatchTools(session)
        withContext(RunToolScope(AgentRunId("typed-restore"), targetIsLocal = true)) {
            val view = snapshot()
            val prepared = executor.execute(ToolCall("prepare", NotebookBatchTools.READ,
                mapOf("expectedSnapshot" to view, "operation" to "RESTORE"))) as ToolExecutionResult.Success
            val applied = executor.execute(ToolCall("restore", NotebookBatchTools.WRITE,
                mapOf("expectedSnapshot" to view, "expectedBatch" to JSONObject(prepared.output).getString("batchSnapshot"),
                    "operation" to "RESTORE")), ToolExecutionContext("approved", directUserAuthority = true))
            assertTrue(applied is ToolExecutionResult.Success)
        }
        ids.forEach { assertEquals("ACTIVE", lifecycle(it)) }
        model.setFilter(NotebookFilter.ALL); visible(ids, NotebookFilter.ALL); select(ids)
        model.applyToSelection(NotebookBatchAction.TRASH).join()
        assertEquals(ids, model.trashUndoIds.value)
        model.setFilter(NotebookFilter.TRASH); visible(ids, NotebookFilter.TRASH)
        model.undoLastTrash().join()
        ids.forEach { assertEquals("ACTIVE", lifecycle(it)) }
        assertTrue(model.trashUndoIds.value.isEmpty())
    }

    @Test fun bundledAndRuntimeDiscoveryKeepBatchReadOnlyAndWritesDestructive() {
        val skill = DefaultSkillRegistry.defaultBuiltInSkills().single { it.id.value == "halo.library" }
        val manifest = JSONObject(f.context.assets.open("skills/halo.library.json").bufferedReader().use { it.readText() })
        assertEquals("DESTRUCTIVE", skill.risk.name)
        assertEquals("DESTRUCTIVE", manifest.getString("risk"))
        NotebookBatchTools.NAMES.forEach { name ->
            val expected = if (name == NotebookBatchTools.READ) "READ_ONLY" else "DESTRUCTIVE"
            assertEquals(expected, skill.toolDefinitions.single { it.toolId == name }.risk.name)
            val definitions = manifest.getJSONArray("toolDefinitions")
            val entry = (0 until definitions.length()).map { definitions.getJSONObject(it) }.single { it.getString("toolId") == name }
            assertEquals(expected, entry.getString("risk"))
            assertTrue(name in PersonalConfigurationToolAccess.library)
        }
    }

    @Test fun typedBatchNoticeClearsWithDismissalAndCannotMaskANewerOrdinaryNotice() = runBlocking {
        val note = f.notes.createNote("one").id
        visible(setOf(note)); select(setOf(note))
        val first = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.CONTEXT))
        val receipt = requireNotNull(model.applyLibraryBatch(first))
        assertEquals(receipt, model.batchNotice.value)
        model.clearNotice()
        assertNull(model.batchNotice.value)
        assertNull(model.notice.value)
        visible(setOf(note)); select(setOf(note))
        val second = requireNotNull(model.prepareLibraryBatch(NotebookBatchAction.CONTEXT))
        model.applyLibraryBatch(second)
        assertNotNull(model.batchNotice.value)
        // This fixture deliberately has no purge owner: the existing purge operation emits an ordinary refusal notice.
        model.deletePermanently(note).join()
        assertNull(model.batchNotice.value)
        assertNotNull(model.notice.value)
        assertEquals("ACTIVE", lifecycle(note))
    }

    private object NoSearch : WorkspaceSearchEngine {
        override suspend fun search(query: WorkspaceSearchQuery) = WorkspaceSearchResult(query.query, emptyList())
        override suspend fun indexObject(objectId: WorkspaceObjectId, type: WorkspaceObjectType, title: String, content: String, updatedAt: Long) = Unit
        override suspend fun removeObject(objectId: WorkspaceObjectId) = Unit
    }
}
