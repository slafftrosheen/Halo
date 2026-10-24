package com.halo.app.notebook

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.di.tools.*
import com.halo.host.android.AndroidStorage
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Only UUID-scoped synthetic owners; no global notes, model, mic or provider. */
@RunWith(AndroidJUnit4::class)
class NoteEditorControlsDeviceTest {
    @Test fun editsReferencesAndRecoveryGuardsSurviveDatabaseRecreation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val parameters = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(parameters.getString("runId")); val stamp = requireNotNull(parameters.getString("sourceStamp"))
        val name = "note-controls-${UUID.randomUUID()}.db"
        val storage = AndroidStorage(context, name = "note-controls-${UUID.randomUUID()}")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            var documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            var notes = RoomNotebookRepository(db.notebookDao(), documents = documents)
            var workspaceContext = WorkspaceContextStore(db, notes, storage)
            var actions = WorkspaceNoteActions(db, documents, NotebookUseCases(notes), workspaceContext)
            fun registry() = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }.let { descriptors ->
                ToolRegistry(listOf(actions), descriptors).also {
                    assertTrue(it.operabilityReport(listOf(ToolFeatureOperability("notes", descriptors.map { descriptor -> descriptor.name }.toSet()))).isEmpty())
                }
            }
            var tools = registry()
            suspend fun call(name: String, fields: Map<String, Any>): JSONObject {
                val toolCall = ToolCall(UUID.randomUUID().toString(), name, fields)
                val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { tools.descriptor(name) })
                val result = tools.executeAuthorized(policy.authorize(toolCall) as ToolAuthorization.Authorized)
                assertTrue(result.toString(), result is ToolExecutionResult.Success)
                return JSONObject((result as ToolExecutionResult.Success).output)
            }
            val note = notes.createNote("Original paragraph")
            val source = notes.createNote("Original source remains")
            suspend fun args(): Map<String, Any> = mapOf("noteId" to note.id, "version" to call("halo_note_get", mapOf("noteId" to note.id)).getString("version"))
            val first = call("halo_note_get", mapOf("noteId" to note.id)).getJSONArray("blocks").getJSONObject(0).getString("blockId")
            val old = args()
            call("halo_note_edit_block", old + mapOf("blockId" to first, "text" to "Saved learner edit"))
            val stale = ToolCall("stale", "halo_note_rename", old + ("title" to "Stale overwrite"))
            assertTrue(tools.executeAuthorized(ToolAuthorization.Authorized(stale)) is ToolExecutionResult.Rejected)
            call("halo_note_insert_reference", args() + mapOf("targetId" to source.id, "targetVersion" to requireNotNull(workspaceContext.versionFor(source.id)), "afterBlockId" to first))
            call("halo_note_rename", args() + ("title" to "Saved title"))
            val saved = requireNotNull(documents.load(note.id))
            documents.saveRecovery(note.id, saved.blocks.map { if (it.id == first) it.copy(text = "Uncommitted draft") else it }, saved.revision, 7, "Draft title")
            val blocked = ToolCall("recovery", "halo_note_archive", args())
            assertTrue(tools.executeAuthorized(ToolAuthorization.Authorized(blocked)) is ToolExecutionResult.Rejected)
            db.close(); db = open()
            documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            notes = RoomNotebookRepository(db.notebookDao(), documents = documents)
            workspaceContext = WorkspaceContextStore(db, notes, storage)
            actions = WorkspaceNoteActions(db, documents, NotebookUseCases(notes), workspaceContext)
            tools = registry()
            val restored = call("halo_note_get", mapOf("noteId" to note.id))
            assertEquals("Saved title", restored.getString("title")); assertTrue(restored.getBoolean("hasRecoveryDraft"))
            assertEquals(saved, documents.load(note.id))
            assertEquals("Uncommitted draft", documents.loadRecovery(note.id)!!.blocks.first().text)
            assertEquals(source.id, documents.load(note.id)!!.blocks.last().refObjectId)
            assertEquals("Original source remains", notes.getNote(source.id)!!.body)
            File(context.filesDir, "note-editor-controls-evidence.jsonl").appendText(JSONObject()
                .put("scope", "ISOLATED_NOTE_EDITOR_CONTROLS").put("runId", runId).put("sourceStamp", stamp).put("status", "PASS")
                .put("registeredTools", actions.definitions().size).put("databaseRecreated", true).put("savedBlockRetained", true)
                .put("sourceRetained", true).put("staleEditRejected", true).put("recoveryDraftRetained", true)
                .put("spokenRecognition", "NOT_RUN").put("providerExecution", "NOT_RUN").put("osProcessDeath", "NOT_RUN").toString() + "\n")
        } finally { db.close(); context.deleteDatabase(name); storage.clear() }
    }
}
