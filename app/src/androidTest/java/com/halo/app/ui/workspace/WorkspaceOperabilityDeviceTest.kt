package com.halo.app.ui.workspace

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.data.study.StudyRepository
import com.halo.app.di.tools.*
import com.halo.app.study.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.PreparedImageInput
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

/** Isolated native owners; no voice recognition, provider, user data or OS permission changes. */
@RunWith(AndroidJUnit4::class)
class WorkspaceOperabilityDeviceTest {
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    @Test fun nativeCanvasAndExactAttachedStudyUseTheRegisteredSemanticOwners() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "operability-device-" + UUID.randomUUID()).also { it.mkdirs() }
        try {
            val documents = NotebookDocumentRepository(database.notebookDocumentDao(), database)
            val notes = RoomNotebookRepository(database.notebookDao(), documents = documents)
            val workspace = RoomWorkspaceRepository(database.notebookDao())
            val files = RoomWorkspaceFileRepository(context, database, root, ownedUri = Uri::fromFile)
            val canvases = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
            val selected = WorkspaceContextStore(database, notes, MemoryStorage())
            val pdf = object : StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("No PDF")
                override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = error("No PDF")
            }
            val study = StudyUseCases(StudyRepository(database, database.studyDao()), sourceResolver = WorkspaceStudySourceResolver(context, documents, workspace, files, pdf))
            val actions = WorkspaceToolExecutor(notes, workspace, selected, canvas = WorkspaceCanvasActions(database, canvases),
                exactStudyStarter = { id, version -> study.startExactMaterial(id, version, selected::versionFor) })
            val descriptors = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
            val registry = ToolRegistry(listOf(actions), descriptors)
            assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("workspace", descriptors.map { it.name }.toSet()))).isEmpty())
            suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall("native-fixture", name, args))
            val board = JSONObject((call("halo_canvas_create", mapOf("title" to "Synthetic board")) as ToolExecutionResult.Success).output)
            val card = JSONObject((call("halo_canvas_add_text", mapOf("canvasId" to board.getString("canvasId"), "version" to board.getString("version"), "text" to "Original", "x" to 0, "y" to 0)) as ToolExecutionResult.Success).output)
            val changed = call("halo_canvas_edit_text", mapOf("canvasId" to board.getString("canvasId"), "version" to card.getString("version"), "nodeId" to card.getString("nodeId"), "text" to "Reviewed"))
            assertTrue(changed is ToolExecutionResult.Success)
            assertTrue(call("halo_canvas_edit_text", mapOf("canvasId" to board.getString("canvasId"), "version" to card.getString("version"), "nodeId" to card.getString("nodeId"), "text" to "Stale")) is ToolExecutionResult.Rejected)
            assertEquals("Reviewed", canvases.observe(board.getString("canvasId")).first()!!.nodes.single().text)
            val boardId = board.getString("canvasId")
            val sketch = canvases.addSketchNode(boardId, 0f, 0f, "sketch:v1|100,100;200,200")
            val sketchVersion = "canvas:${database.notebookCanvasDao().getDocument(boardId)!!.revision}"
            val clearArgs = mapOf("canvasId" to boardId, "version" to sketchVersion, "nodeId" to sketch)
            val clearPolicy = requireNotNull(registry.descriptor("halo_canvas_clear_sketch")).security
            assertEquals(ConfirmationPolicy.ALWAYS, clearPolicy.confirmation)
            assertEquals(ReplaySafety.NEVER_REPLAY, clearPolicy.replaySafety)
            // Isolated semantic-owner fixture only; actual approval/STT/Power acceptance is separate.
            val cleared = JSONObject((call("halo_canvas_clear_sketch", clearArgs) as ToolExecutionResult.Success).output)
            assertTrue(cleared.getBoolean("changed"))
            assertTrue(requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(canvases.observe(boardId).first()!!.nodes.single { it.id == sketch }.text)).strokes.isEmpty())
            assertTrue(call("halo_canvas_clear_sketch", clearArgs) is ToolExecutionResult.Rejected)
            assertEquals("Reviewed", canvases.observe(boardId).first()!!.nodes.single { it.id == card.getString("nodeId") }.text)
            suspend fun edit(name: String, fields: Map<String, Any>) = JSONObject((call(name,
                mapOf("canvasId" to boardId, "version" to "canvas:${database.notebookCanvasDao().getDocument(boardId)!!.revision}") + fields) as ToolExecutionResult.Success).output)
            edit("halo_canvas_rename", mapOf("title" to "Saved native board"))
            assertEquals("Saved native board", canvases.observe(boardId).first()!!.title)
            val group = edit("halo_canvas_add_group", mapOf("title" to "Native group", "x" to -500, "y" to -400)).getString("nodeId")
            val selectedCards = listOf(card.getString("nodeId"), sketch)
            val tag = edit("halo_canvas_tag", mapOf("nodeIds" to selectedCards, "label" to "Review")).getString("nodeId")
            val copy = edit("halo_canvas_duplicate", mapOf("nodeId" to card.getString("nodeId"))).getString("nodeId")
            edit("halo_canvas_arrange", mapOf("nodeIds" to selectedCards, "operation" to "GRID"))
            edit("halo_canvas_arrange", mapOf("nodeIds" to selectedCards, "operation" to "BRING_FORWARD"))
            val memberPositions = canvases.observe(boardId).first()!!.nodes.filter { it.id in selectedCards }.associate { it.id to (it.x to it.y) }
            selectedCards.forEach { canvases.connect(boardId, group, it, "group") }
            edit("halo_canvas_move", mapOf("nodeId" to group, "x" to -400, "y" to -300))
            val movedMembers = canvases.observe(boardId).first()!!.nodes.filter { it.id in selectedCards }
            movedMembers.forEach { assertEquals(memberPositions.getValue(it.id).let { p -> p.first + 100f to p.second + 100f }, it.x to it.y) }
            val edge = canvases.connect(boardId, group, copy, "Remove this link")
            edit("halo_canvas_disconnect", mapOf("edgeId" to edge))
            assertTrue(canvases.observe(boardId).first()!!.edges.none { it.id == edge })
            edit("halo_canvas_ungroup", mapOf("nodeIds" to listOf(group)))
            assertTrue(canvases.observe(boardId).first()!!.nodes.any { it.id == copy })
            val beforeDelete = "canvas:${database.notebookCanvasDao().getDocument(boardId)!!.revision}"
            edit("halo_canvas_delete", mapOf("nodeIds" to listOf(copy, tag)))
            val remaining = canvases.observe(boardId).first()!!
            assertEquals(selectedCards.toSet(), remaining.nodes.map { it.id }.toSet())
            assertEquals("Reviewed", remaining.nodes.single { it.id == card.getString("nodeId") }.text)
            assertTrue(remaining.edges.isEmpty())
            assertTrue(call("halo_canvas_delete", mapOf("canvasId" to boardId, "version" to beforeDelete, "nodeIds" to selectedCards)) is ToolExecutionResult.Rejected)
            val note = notes.createNote("Synthetic lesson material", title = "Study source")
            database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("native-audio", note.id, "AUDIO", "audio/wav", "MANAGED_FILE", "synthetic-no-read", true, "Audio", 10, null, null, 100, 1))
            database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("native-audio-link", note.id, "native-audio", 0, null, 1))
            val noteReader = WorkspaceNoteActions(database, documents, com.halo.app.notebook.NotebookUseCases(notes), selected)
            val noteReceipt = JSONObject((noteReader.execute(ToolCall("native-note-read", "halo_note_get",
                mapOf("noteId" to note.id))) as ToolExecutionResult.Success).output)
            val version = noteReceipt.getString("sourceVersion")
            assertEquals(selected.versionFor(note.id), version)
            assertTrue(version.contains(":"))
            assertTrue(call("halo_study_with_halo", mapOf("noteId" to note.id, "sourceVersion" to version)) is ToolExecutionResult.Success)
            assertEquals(note.revision.toString(), study.observeActiveSessions().first().single().sources.single().revision)
            assertTrue(database.memoryRecordDao().observeActiveRecords().first().isEmpty())
        } finally {
            database.close()
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }
}
