package com.halo.app.notebook

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.di.tools.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** UUID-scoped registered owner calls; no actual chat/STT/provider or user files. */
@RunWith(AndroidJUnit4::class)
class NoteAttachmentControlsDeviceTest {
    @Test fun reuseAndUnlinkKeepDurableOwnershipUntilBothNotesArePurged() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parameters = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(parameters.getString("runId")); val stamp = requireNotNull(parameters.getString("sourceStamp"))
        val name = "attachment-controls-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "attachment-controls-${UUID.randomUUID()}").also { check(it.mkdir()) }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            var assets = NotebookAssetRepository(context, db)
            var actions = WorkspaceNoteAttachmentActions(assets)
            fun registry(): ToolRegistry {
                val descriptors = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
                return ToolRegistry(listOf(actions), descriptors).also {
                    assertTrue(it.operabilityReport(listOf(ToolFeatureOperability("attachments", descriptors.map { row -> row.name }.toSet()))).isEmpty())
                }
            }
            var tools = registry()
            suspend fun call(name: String, fields: Map<String, Any>): JSONObject {
                val result = tools.executeAuthorized(ToolAuthorization.Authorized(ToolCall(UUID.randomUUID().toString(), name, fields)))
                assertTrue(result.toString(), result is ToolExecutionResult.Success)
                return JSONObject((result as ToolExecutionResult.Success).output)
            }
            suspend fun state(id: String) = call("halo_note_attachments", mapOf("noteId" to id))
            val notes = RoomNotebookRepository(db.notebookDao())
            val source = notes.createNote("Saved source").id; val target = notes.createNote("Saved target").id
            val file = File(root, "owned-image").also { it.writeText("Synthetic image bytes; no decoder") }
            db.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("image", source, "IMAGE", "image/png", "MANAGED_FILE", file.path, true, "Image", file.length(), 1, 1, null, 1))
            db.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("link", source, "image", 0, null, 1))
            val oldTarget = state(target).getString("version")
            val fields = mapOf("noteId" to target, "version" to oldTarget, "sourceNoteId" to source, "sourceVersion" to state(source).getString("version"), "attachmentId" to "link")
            val saved = call("halo_note_reuse_image", fields)
            assertTrue(saved.getBoolean("changed")); assertFalse(saved.getBoolean("fileCleanupPerformed"))
            assertTrue(tools.executeAuthorized(ToolAuthorization.Authorized(ToolCall("stale", "halo_note_reuse_image", fields))) is ToolExecutionResult.Rejected)
            assertTrue(assets.remove(source, "link")) // Same existing editor owner.
            assertEquals(1, db.notebookDao().getAssetsForObject(source).size)
            db.close(); db = open()
            assets = NotebookAssetRepository(context, db); actions = WorkspaceNoteAttachmentActions(assets); tools = registry()
            assertEquals(saved.getString("version"), state(target).getString("version"))
            assertEquals(1, assets.observe(target).first().size); assertTrue(file.exists())
            val reopenedNotes = RoomNotebookRepository(db.notebookDao())
            reopenedNotes.setLifecycle(source, WorkspaceLifecycle.TRASHED)
            val purge = NotebookPurgeOwner(db, root)
            val retained = purge.purge(source) as NotebookPurgeResult.Saved
            assertEquals(1, retained.receipt.record.sharedRetained); assertTrue(file.exists())
            val unlink = mapOf("noteId" to target, "version" to state(target).getString("version"), "attachmentId" to saved.getString("attachmentId"))
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { tools.descriptor(it) })
            assertTrue(policy.authorize(ToolCall("unattended", "halo_note_unlink_attachment", unlink)) is ToolAuthorization.Rejected)
            val removed = call("halo_note_unlink_attachment", unlink)
            assertTrue(removed.getBoolean("changed")); assertFalse(removed.getBoolean("fileCleanupPerformed"))
            assertTrue(assets.observe(target).first().isEmpty()); assertEquals(1, db.notebookDao().getAssetsForObject(target).size)
            reopenedNotes.setLifecycle(target, WorkspaceLifecycle.TRASHED)
            assertEquals("COMPLETE", (purge.purge(target) as NotebookPurgeResult.Saved).receipt.record.state)
            assertFalse(file.exists())
            File(context.filesDir, "note-attachment-controls-evidence.jsonl").appendText(JSONObject()
                .put("scope", "ISOLATED_NOTE_ATTACHMENT_CONTROLS").put("runId", runId).put("sourceStamp", stamp).put("status", "PASS")
                .put("databaseRecreated", true).put("staleReuseRejected", true).put("sharedOwnershipRetained", true)
                .put("lastOwnerPurgeClearedFile", true).put("unattendedUnlinkRejected", true)
                .put("actualTypedIntent", "NOT_RUN").put("spokenRecognition", "NOT_RUN").put("providerExecution", "NOT_RUN")
                .put("osProcessDeath", "NOT_RUN").toString() + "\n")
        } finally { db.close(); context.deleteDatabase(name); check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively() }
    }
}
