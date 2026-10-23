package com.halo.app.data.workspace

import com.halo.app.di.tools.*
import com.halo.app.data.study.StudyRepository
import com.halo.app.study.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Typed semantic-owner workflows. These do not attest physical STT or a permitted cloud provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceAssistantWorkflowsTest {
    private suspend fun json(executor: ToolExecutor, name: String, arguments: Map<String, Any> = emptyMap()) =
        JSONObject((executor.execute(ToolCall("fixture", name, arguments)) as ToolExecutionResult.Success).output)

    @Test fun textCreateEditExportCanvasAndStudyUseTheSameSavedSource() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val files = WorkspaceFileEditingActions(f.tools)
            val canvas = WorkspaceCanvasActions(f.database, f.canvases)
            val created = json(files, "halo_text_create", mapOf("title" to "Trip notes", "format" to "MARKDOWN", "text" to "# Trip"))
            val edited = json(files, "halo_text_edit", mapOf("noteId" to created.getString("noteId"), "revision" to created.getLong("revision"), "operation" to "replace", "fields" to mapOf("text" to "# Trip\nBring a map")))
            val exported = json(files, "halo_text_export", mapOf("noteId" to edited.getString("noteId"), "revision" to edited.getLong("revision"), "format" to "MARKDOWN"))
            val board = json(canvas, "halo_canvas_create", mapOf("title" to "Trip board"))
            json(files, "halo_file_attach_canvas", mapOf("canvasId" to board.getString("canvasId"), "canvasVersion" to board.getString("version"), "objectId" to exported.getString("objectId"), "version" to exported.getString("version")))
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), sourceResolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()))
            val workspace = WorkspaceToolExecutor(f.notes, f.workspace, f.contextOwner(), exactStudyStarter = { id, version ->
                study.startExactMaterial(id, version, f.contextOwner()::versionFor)
            })
            assertTrue(workspace.execute(ToolCall("fixture", "halo_study_with_halo", mapOf("noteId" to exported.getString("objectId"), "sourceVersion" to exported.getString("version")))) is ToolExecutionResult.Success)
            assertEquals(exported.getString("objectId"), study.observeActiveSessions().first().single().sources.single().objectId)
            assertEquals(exported.getString("objectId"), f.canvases.observe(board.getString("canvasId")).first()!!.nodes.single().refObjectId)
            assertTrue(f.continuity().describe(exported.getString("objectId")).sources.any { it.objectId == edited.getString("noteId") })
        }
    }
    @Test fun photoStudioEditExportAndCanvasHaveExactResultReceipts() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.png("Original photo")
            val original = f.bytes(source)
            val studio = StudioToolExecutor(f.studio())
            val project = json(studio, "halo_studio_create", mapOf("title" to "Artwork", "sourceId" to source.objectId.value, "sourceVersion" to source.version!!))
            val edited = json(studio, "halo_studio_edit", mapOf("projectId" to project.getString("projectId"), "revision" to project.getLong("revision"), "operation" to "background", "fields" to mapOf("color" to "#ffffff")))
            val output = json(studio, "halo_studio_export", mapOf("projectId" to edited.getString("projectId"), "revision" to edited.getLong("revision"), "format" to "PNG"))
            val board = f.canvases.create("Art board")
            val inserted = json(studio, "halo_studio_insert", mapOf("canvasId" to board, "canvasVersion" to "canvas:1", "outputId" to output.getString("outputId"), "fileVersion" to output.getString("fileVersion")))
            assertEquals(inserted.getString("nodeId"), f.canvases.observe(board).first()!!.nodes.single().id)
            assertEquals(output.getString("outputId"), f.canvases.observe(board).first()!!.nodes.single().refObjectId)
            assertArrayEquals(original, f.bytes(source))
            assertTrue(f.continuity().describe(output.getString("outputId")).sources.any { it.objectId == source.objectId.value })
        }
    }
    @Test fun exactImageCopiesCollageAndSelectedContextKeepBothOriginals() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.png("First")
            val second = f.png("Second")
            val firstBytes = f.bytes(first); val secondBytes = f.bytes(second)
            val files = WorkspaceFileEditingActions(f.tools)
            val transformed = json(files, "halo_image_transform", mapOf("objectId" to first.objectId.value, "version" to first.version!!, "recipe" to mapOf("quarterTurns" to 1, "format" to "PNG")))
            val collage = json(files, "halo_image_collage", mapOf("sources" to listOf(
                mapOf("objectId" to transformed.getString("objectId"), "version" to transformed.getString("version")),
                mapOf("objectId" to second.objectId.value, "version" to second.version!!)), "recipe" to mapOf("mode" to "GRID", "format" to "PNG")))
            val context = f.contextOwner()
            val links = WorkspaceContinuityActions(f.continuity(context))
            json(links, "halo_workspace_select", mapOf("objectId" to collage.getString("objectId"), "version" to collage.getString("version")))
            assertEquals(listOf(collage.getString("objectId")), f.database.workspaceContextDao().selectedIds())
            val sources = f.continuity().describe(collage.getString("objectId")).sources.map { it.objectId }.toSet()
            assertTrue(first.objectId.value in sources && second.objectId.value in sources)
            assertArrayEquals(firstBytes, f.bytes(first)); assertArrayEquals(secondBytes, f.bytes(second))
            assertTrue(f.database.memoryRecordDao().observeActiveRecords().first().isEmpty())
        }
    }
}
