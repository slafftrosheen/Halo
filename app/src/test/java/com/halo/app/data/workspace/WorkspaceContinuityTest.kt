package com.halo.app.data.workspace

import android.graphics.Color
import com.halo.app.capture.WorkspaceMediaAssetResolver
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.studio.*
import com.halo.app.data.memory.*
import com.halo.app.study.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

internal class ContinuityTestStorage : Storage {
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
internal fun WorkspaceFileToolsFixture.contextOwner() = WorkspaceContextStore(database, notes, ContinuityTestStorage())
internal fun WorkspaceFileToolsFixture.continuity(context: WorkspaceContextStore = contextOwner()) = WorkspaceContinuity(database, context, files, tools, WorkspaceHandoffRequests(scope, {}))
internal fun WorkspaceFileToolsFixture.studio() = StudioService(context, StudioProjectRepository(database), files, workspace,
    StudioRenderer(context, files, workspace), NotebookAssetRepository(context, database), canvases, database.workspaceContextDao(), StudioUiRequests(scope, {}))
internal fun noPdf() = object : StudyPdfPages {
    override suspend fun pageCount(file: WorkspaceFile): Int = error("No PDF in this fixture")
    override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = error("No PDF in this fixture")
}
internal fun noEphemeral() = object : MediaAssetResolver {
    override suspend fun metadata(ref: MediaAssetRef): MediaAssetMetadata? = null
    override suspend fun open(ref: MediaAssetRef): MediaAssetContent = error("No ephemeral content in this fixture")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceContinuityTest {
    @Test fun photoStudioSaveReopenExportCanvasStudyAndChatPreserveIdentityAndOriginal() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val photo = f.png("Synthetic photo")
            val bytes = f.bytes(photo)
            val token = UUID.randomUUID().toString()
            val studio = f.studio()
            val created = studio.createFromImage("Continued artwork", photo.objectId.value, photo.version!!, token)
            val changed = studio.projects.apply(created.id, created.revision, StudioCommand.Background(Color.BLUE))
            val saved = studio.projects.save(changed.id, changed.revision)
            val reopened = StudioProjectRepository(f.database).get(saved.id)!!
            assertEquals(saved, reopened)
            val output = studio.export(reopened.id, reopened.revision, CanvasImageFormat.PNG)
            val context = f.contextOwner()
            val continuity = f.continuity(context)
            val board = f.canvases.create("Synthetic board")
            val boardVersion = context.versionFor(board)!!
            val node = continuity.insert(board, boardVersion, output.objectId.value, output.version!!)
            assertEquals(output.objectId.value, f.canvases.observe(board).first()!!.nodes.single { it.id == node }.refObjectId)
            assertTrue(runCatching { continuity.insert(board, boardVersion, output.objectId.value, output.version!!) }.isFailure)
            val material = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()).resolveExplicitReference(output.objectId.value, "") as ResolvedStudySource.Visual
            assertEquals(output.version, material.revision)
            val media = WorkspaceMediaAssetResolver(f.context, f.files, noEphemeral(), NotebookAssetRepository(f.context, f.database), f.workspace)
            assertArrayEquals(f.bytes(output), media.open(material.asset).bytes)
            assertArrayEquals(bytes, f.bytes(photo))
            val history = continuity.describe(output.objectId.value).sources
            assertTrue(history.any { it.objectId == saved.id && it.version == saved.revision.toString() })
            assertTrue(history.any { it.objectId == photo.objectId.value && it.version == photo.version && it.state == WorkspaceSourceState.CURRENT })
            val found = RoomWorkspaceRepository(f.database.notebookDao()).observe(WorkspaceQuery(search = "Continued artwork")).first().map { it.id.value }
            assertTrue(saved.id in found && output.objectId.value in found)
            assertTrue(f.database.memoryRecordDao().observeActiveRecords().first().isEmpty())
        }
    }
    @Test fun completedHandoffReopensItsResultAfterOwnerRestartWithoutUndoingUserEdits() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val photo = f.png("Photo")
            val token = UUID.randomUUID().toString()
            val firstOwner = f.studio()
            val first = firstOwner.createFromImage("Opened photo", photo.objectId.value, photo.version!!, token)
            val edited = firstOwner.projects.apply(first.id, first.revision, StudioCommand.Background(Color.BLUE))
            val reopened = f.studio().createFromImage("Opened photo", photo.objectId.value, photo.version!!, token)
            assertEquals(edited, reopened)
            assertEquals(1, firstOwner.projects.observeProjects().first().size)
            assertEquals(first.id, f.continuity().handoffResult(token)!!.objectId)
            assertTrue(runCatching { f.studio().createFromImage("Different intent", photo.objectId.value, photo.version!!, token) }.isFailure)
            assertTrue(runCatching { f.studio().createFromImage("Opened photo", photo.objectId.value, "stale", token) }.isFailure)
            assertEquals(1, firstOwner.projects.observeProjects().first().size)
        }
    }
    @Test fun trashRejectsNewSourceUseButRetainedOutputKeepsHonestHistoricalLink() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val photo = f.png("Photo")
            val studio = f.studio()
            val project = studio.createFromImage("Artwork", photo.objectId.value, photo.version!!)
            val output = studio.export(project.id, project.revision, CanvasImageFormat.PNG)
            f.workspace.trash(photo.objectId)
            assertTrue(runCatching { f.studio().createFromImage("No reuse", photo.objectId.value, photo.version!!) }.isFailure)
            assertTrue(runCatching { studio.export(project.id, project.revision, CanvasImageFormat.PNG) }.isFailure)
            val source = f.continuity().describe(output.objectId.value).sources.single { it.objectId == photo.objectId.value }
            assertEquals(WorkspaceSourceState.UNAVAILABLE, source.state)
            assertTrue(f.bytes(output).isNotEmpty())
        }
    }
    @Test fun workingSetKeepsLiteralTextAndRejectsChangedOrTrashedObjects() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val context = f.contextOwner()
            val owner = f.continuity(context)
            val document = f.text.create("Literal", WorkspaceTextFormat.MARKDOWN, "# raw\n```literal```!!")
            owner.selectWorkingSet(document.id, document.revision.toString())
            val content = context.contentFor(document.id)!!
            assertTrue(content.endsWith(document.text))
            assertFalse(content.contains("halo-file-md-v1"))
            f.text.replace(document.id, document.revision, "changed")
            assertTrue(runCatching { owner.selectWorkingSet(document.id, document.revision.toString()) }.isFailure)
            f.workspace.trash(WorkspaceObjectId(document.id))
            assertNull(context.contentFor(document.id))
            assertNull(context.versionFor(document.id))
            assertTrue(context.entries(false).isEmpty())
        }
    }
    @Test fun staleInkSelectionDoesNotHideItsActiveCanvasDestination() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val canvas = f.canvases.create("Board")
            val context = f.contextOwner()
            f.database.workspaceContextDao().add(com.halo.app.data.WorkingSetEntry(canvas, 1, "INK_SELECTION:missing"))
            assertNull(context.versionFor(canvas))
            val owner = f.continuity(context)
            assertEquals("canvas:1", owner.describe(canvas).version)
            assertTrue(owner.canvases.first().any { it.objectId == canvas })
        }
    }
    @Test fun canvasReviewedNotesKeepTheirCanonicalDerivedIdAndSourceNodeHistory() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val canvas = f.canvases.create("Board")
            val node = f.canvases.addTextNode(canvas, 0f, 0f, "Original idea")
            val operation = UUID.randomUUID().toString()
            f.canvases.saveReviewedText(canvas, node, "Original idea", "Reviewed result", "Summary", operation)
            val noteId = "$operation:note"
            val owner = f.continuity()
            val item = owner.describe(noteId)
            assertEquals(noteId, item.objectId)
            assertEquals(node, item.sources.single().nodeId)
            assertEquals(WorkspaceSourceState.CURRENT, item.sources.single().state)
            assertTrue(owner.route(noteId, item.version, WorkspaceDestination.OPEN).startsWith("notebook/note/"))
            val output = f.text.exportNote(noteId, 1, WorkspaceTextFormat.TXT)
            assertEquals("Reviewed result", f.bytes(output).toString(Charsets.UTF_8))
            f.canvases.updateText(canvas, node, "Changed idea", "Original idea")
            assertEquals(WorkspaceSourceState.EARLIER_VERSION, owner.describe(noteId).sources.single().state)
        }
    }
    @Test fun existingJsonTextRetainsChatAndStudyWithoutAdvertisingUnsupportedEditing() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val document = f.text.create("Config", WorkspaceTextFormat.TXT, "{\"name\":\"Synthetic\"}")
            val output = f.text.export(document.id, document.revision)
            val row = f.database.workspaceFileDao().getFile(output.objectId.value)!!
            f.database.workspaceFileDao().insertFile(row.copy(displayName = "config.json", mimeType = "application/json"))
            val item = f.continuity().describe(output.objectId.value)
            assertTrue(WorkspaceDestination.STUDY in item.destinations)
            assertTrue(WorkspaceDestination.CHAT in item.destinations)
            assertFalse(WorkspaceDestination.FILE_TOOLS in item.destinations)
            val source = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()).resolveExplicitReference(output.objectId.value, "Synthetic")
            assertTrue(source is ResolvedStudySource.Text)
        }
    }
    @Test fun memoryPromotionRequiresLiveExactSourceAndForgettingStopsRetrieval() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.notes.createNote("Tea is preferred", title = "Preference")
            val memory = RoomMemoryRepository(f.database)
            val candidate = MemoryCandidate("Tea is preferred", WorkspaceObjectId(note.id), note.revision)
            assertTrue(runCatching { memory.promoteCandidate(candidate.copy(sourceRevision = note.revision + 1)) }.isFailure)
            val record = memory.promoteCandidate(candidate)
            val context = f.contextOwner()
            context.select(record.id)
            assertNotNull(context.contentFor(record.id))
            assertTrue(memory.forgetMemory(record.id))
            assertNull(context.contentFor(record.id))
            assertNull(context.versionFor(record.id))
            assertTrue(context.entries(true).isEmpty())
            f.workspace.trash(WorkspaceObjectId(note.id))
            assertTrue(runCatching { memory.promoteCandidate(candidate) }.isFailure)
        }
    }
}
