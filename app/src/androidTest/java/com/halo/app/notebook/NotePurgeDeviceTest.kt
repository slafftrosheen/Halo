package com.halo.app.notebook

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.di.tools.*
import com.halo.app.storage.haloStorageLayout
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

/** Synthetic registered calls on isolated UUID data. No user notes, microphone or providers. */
@RunWith(AndroidJUnit4::class)
class NotePurgeDeviceTest {
    @Test fun committedDeletionResumesAfterRoomRecreationAndPreservesActualFileProviderReaders() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parameters = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(parameters.getString("runId"))
        val stamp = requireNotNull(parameters.getString("sourceStamp"))
        val name = "note-purge-${UUID.randomUUID()}.db"
        val storage = AndroidStorage(context, name = "note-purge-${UUID.randomUUID()}")
        val root = haloStorageLayout(context).managedWorkspaceFiles.also { check(it.isDirectory || it.mkdirs()) }
        val directory = File(root, "purge-fixture-${UUID.randomUUID()}").also { check(it.mkdir()) }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            var fail = true
            var scheduled = 0
            fun owner() = NotebookPurgeOwner(db, root, schedule = { scheduled++ }, removeFile = {
                if (fail) error("Fixture temporarily unavailable")
                check(it.delete())
            })
            var purge = owner()
            val documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            val notes = RoomNotebookRepository(db.notebookDao(), documents = documents, purgeOwner = purge)
            val useCases = NotebookUseCases(notes)
            val reads = WorkspaceNoteActions(db, documents, useCases, WorkspaceContextStore(db, notes, storage))
            val actions = WorkspaceNotePurgeActions(useCases)
            val executors = listOf(reads, actions)
            val descriptors = executors.flatMap { it.definitions() }.map { requireNotNull(HaloToolDescriptors.workspace(it)) }
            val tools = ToolRegistry(executors, descriptors)
            assertTrue(tools.operabilityReport(listOf(ToolFeatureOperability("purge", descriptors.map { it.name }.toSet()))).isEmpty())
            suspend fun call(tool: String, args: Map<String, Any>): JSONObject {
                val result = tools.executeAuthorized(ToolAuthorization.Authorized(ToolCall(UUID.randomUUID().toString(), tool, args)))
                assertTrue(result.toString(), result is ToolExecutionResult.Success)
                return JSONObject((result as ToolExecutionResult.Success).output)
            }
            val note = notes.createNote("Isolated saved content")
            val reader = notes.createNote("Isolated shared reader")
            suspend fun asset(id: String, file: File) {
                db.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(id, note.id, "IMAGE", "image/png", "MANAGED_FILE", file.path, true, "Fixture", 7, 1, 1, null, 1))
            }
            val exclusive = File(directory, "exclusive").also { it.writeText("Fixture") }
            val shared = File(directory, "shared space").also { it.writeText("Fixture") }
            asset("a-exclusive", exclusive); asset("b-shared", shared)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", shared)
            assertEquals("Fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            db.workspaceFileDao().insertFile(WorkspaceFileEntity(reader.id, uri.toString(), "Shared", "image/png", 7, 1, null, false))
            notes.setLifecycle(note.id, WorkspaceLifecycle.TRASHED)
            val saved = requireNotNull(documents.load(note.id))
            documents.saveRecovery(note.id, saved.blocks, saved.revision, 1, "Recovery")
            val observed = call("halo_note_get", mapOf("noteId" to note.id))
            val args = mapOf("noteId" to note.id, "version" to observed.getString("version"))
            assertTrue(tools.executeAuthorized(ToolAuthorization.Authorized(ToolCall("missing-choice", "halo_note_purge", args))) is ToolExecutionResult.Rejected)
            assertNotNull(notes.getNote(note.id))
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { tools.descriptor(it) })
            assertTrue(policy.authorize(ToolCall("unattended", "halo_note_purge", args)) is ToolAuthorization.Rejected)
            val approved = args + mapOf("includeRecoveryDraft" to true, "recoveryVersion" to observed.getString("recoveryVersion"))
            val pending = call("halo_note_purge", approved)
            assertTrue(pending.getBoolean("deleted")); assertEquals("PENDING", pending.getString("status")); assertEquals(1, scheduled)
            assertNull(db.notebookDao().getNote(note.id)); assertNull(db.notebookDao().getDraft(note.id))
            assertTrue(exclusive.exists()); assertTrue(shared.exists())
            db.close(); db = open(); fail = false; purge = owner()
            val receipt = requireNotNull(purge.status(note.id))
            assertEquals(pending.getString("receiptVersion"), receipt.version)
            val complete = purge.retry(note.id, receipt.version) as NotebookPurgeResult.Saved
            assertEquals("COMPLETE", complete.receipt.record.state)
            assertEquals(1, complete.receipt.record.exclusiveCleared); assertEquals(1, complete.receipt.record.sharedRetained)
            assertFalse(exclusive.exists()); assertTrue(shared.exists()); assertNotNull(db.notebookDao().getNote(reader.id))
            assertEquals("Fixture", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            assertTrue(purge.retry(note.id, receipt.version) is NotebookPurgeResult.Refused)
            assertTrue((purge.purge(note.id, args.getValue("version"), observed.getString("recoveryVersion"), true) as NotebookPurgeResult.Saved).alreadyRecorded)
            File(context.filesDir, "note-purge-evidence.jsonl").appendText(JSONObject()
                .put("scope", "ISOLATED_NOTE_PURGE").put("runId", runId).put("sourceStamp", stamp).put("status", "PASS")
                .put("databaseRecreated", true).put("exclusiveAbsent", true).put("fileProviderReaderRetained", true)
                .put("draftChoiceGuard", true).put("staleReceiptRejected", true).put("uncertainReplayNoEffect", true)
                .put("workManagerDispatch", "NOT_RUN").put("actualTypedIntent", "NOT_RUN").put("spokenRecognition", "NOT_RUN")
                .put("providerExecution", "NOT_RUN").put("osProcessDeath", "NOT_RUN").toString() + "\n")
        } finally {
            db.close(); context.deleteDatabase(name); storage.clear()
            check(directory.canonicalFile.parentFile == root.canonicalFile); directory.deleteRecursively()
        }
    }
}
