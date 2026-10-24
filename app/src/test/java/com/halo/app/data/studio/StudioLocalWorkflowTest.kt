package com.halo.app.data.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class StudioLocalWorkflowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var projects: StudioProjectRepository
    private lateinit var files: RoomWorkspaceFileRepository
    private lateinit var renderer: StudioRenderer
    private lateinit var service: StudioService
    private lateinit var canvases: NotebookCanvasRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        root = File(context.cacheDir, "studio-test-" + UUID.randomUUID()).also { it.mkdirs() }
        projects = StudioProjectRepository(db)
        files = RoomWorkspaceFileRepository(context, db, root, ownedUri = { file -> Uri.parse("content://studio-fixture/${file.parentFile.name}/${file.name}") })
        val workspace = RoomWorkspaceRepository(db.notebookDao())
        renderer = StudioRenderer(context, files, workspace)
        canvases = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        service = StudioService(context, projects, files, workspace, renderer, NotebookAssetRepository(context, db),
            canvases, db.workspaceContextDao(), StudioUiRequests(scope, {}))
    }
    @After fun close() {
        scope.cancel(); db.close()
        check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
        root.deleteRecursively()
    }
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    private suspend fun readable(file: WorkspaceFile) {
        val source = File(requireNotNull(db.workspaceFileDao().getFile(file.objectId.value)?.ownedCopyPath))
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(file.uri) { source.inputStream() }
    }
    @Test fun inkSelectionRequiresThePublishingSessionsOwnPersistedRevision() = runTest {
        val ink = com.halo.sdk.runtime.ink.InkDocument(listOf(com.halo.sdk.runtime.ink.InkStroke(
            com.halo.sdk.runtime.ink.InkTool.FINGER, listOf(com.halo.sdk.runtime.ink.InkPoint(100, 100)), "stroke")))
        val layer = StudioLayer(id = "drawing", name = "Drawing", kind = StudioLayerKind.INK,
            ink = com.halo.sdk.runtime.ink.InkCodec.encode(ink))
        val initial = projects.create("Selection", StudioDocument(layers = listOf(layer)))
        service.attachInkSelectionOwner(initial.id, "session-owner")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((initial.revision.toString() + "\n" + com.halo.sdk.runtime.ink.InkCodec.encode(ink)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val selection = com.halo.sdk.runtime.ink.InkSelectionContext(initial.id, layer.id,
            com.halo.sdk.runtime.ink.InkSelectionBounds(0, 0, 200, 200), listOf("stroke"), digest,
            com.halo.sdk.runtime.ink.InkSelectionOrigin.BOX, 1, 1)
        assertTrue(service.publishSelection(initial.id, layer.id, initial.revision, "session-owner", selection, initial.revision))
        val external = projects.apply(initial.id, initial.revision, StudioCommand.Background(Color.BLUE))
        assertNull(service.selectedInk(initial.id))
        assertFalse(service.publishSelection(initial.id, layer.id, initial.revision, "session-owner", selection, initial.revision))
        assertNull(service.selectedInk(initial.id))
        // The digest anchor stays frozen during a real input session; its persisted revision advances.
        assertTrue(service.publishSelection(initial.id, layer.id, initial.revision, "session-owner", selection, external.revision))
        assertEquals(external.revision, service.selectedInk(initial.id)?.projectRevision)
        service.detachInkSelectionOwner("session-owner")
        assertNull(service.selectedInk(initial.id))
    }
    @Test fun localImportEditExportReopenAndCanvasInsertRetainExactSource() = runTest {
        val created = projects.create("Local project")
        val input = files.createManagedFile("original.png", "image/png", png(), created.id, "1", "{}")
        readable(input)
        val imported = service.importImage(created.id, created.revision, input.objectId.value)
        val resized = projects.apply(imported.id, imported.revision, StudioCommand.Resize(320, 320))
        val edited = projects.apply(resized.id, resized.revision, StudioCommand.Background(Color.BLUE))
        val preview = renderer.render(edited.session.current, true)
        try {
            assertEquals(320, preview.width)
            assertEquals(Color.BLUE, preview.getPixel(10, 160))
            assertEquals(Color.RED, preview.getPixel(160, 160))
        } finally { preview.recycle() }
        val output = service.export(edited.id, edited.revision, CanvasImageFormat.PNG)
        assertNotEquals(input.objectId, output.objectId)
        assertEquals(input.sha256, files.getFile(input.objectId)?.sha256)
        assertEquals(StudioSource(input.objectId.value, input.sha256!!), projects.get(created.id)?.session?.current?.layers?.single()?.source)
        val provenance = db.workspaceContextDao().provenanceFor(output.objectId.value)!!
        assertEquals(created.id, provenance.sourceId)
        assertEquals(edited.revision.toString(), provenance.revision)
        val canvas = canvases.create()
        val node = service.insertOutput(canvas, output.objectId.value, output.version!!)
        assertEquals(output.objectId.value, canvases.observe(canvas).first()!!.nodes.single { it.id == node }.refObjectId)
        assertTrue(runCatching { service.export(created.id, created.revision, CanvasImageFormat.PNG) }.isFailure)
    }
    @Test fun assistantUsesSameRevisionGuardAndDispatchReceiptsAsLocalEditing() = runTest {
        val tools = com.halo.app.di.tools.StudioToolExecutor(service)
        val definitions = tools.definitions()
        assertEquals(11, definitions.size)
        assertEquals(definitions.size, definitions.map { it.name }.distinct().size)
        definitions.forEach { assertNotNull(com.halo.app.di.tools.HaloToolDescriptors.workspace(it)) }
        val project = projects.create("Assistant project")
        suspend fun call(name: String, arguments: Map<String, Any>) = tools.execute(com.halo.sdk.provider.ToolCall("fixture", name, arguments))
        val changed = call("halo_studio_edit", mapOf("projectId" to project.id, "revision" to project.revision,
            "operation" to "background", "fields" to mapOf("color" to "#ffffff")))
        assertTrue(changed is com.halo.runtime.cognition.tools.ToolExecutionResult.Success)
        val stale = call("halo_studio_edit", mapOf("projectId" to project.id, "revision" to project.revision,
            "operation" to "background", "fields" to mapOf("color" to "#000000")))
        assertTrue(stale is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        assertEquals(Color.WHITE, projects.get(project.id)?.session?.current?.background)
        val invalid = call("halo_studio_open", mapOf("projectId" to project.id, "url" to "https://untrusted"))
        assertTrue(invalid is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        val opened = call("halo_studio_open", mapOf("projectId" to project.id)) as com.halo.runtime.cognition.tools.ToolExecutionResult.Success
        assertEquals(com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED, opened.actionTruth)
        assertNotNull(service.uiRequests.pending.value)
        assertEquals(StudioUiRequestPhase.PENDING_UI, service.uiRequests.receipt(service.uiRequests.pending.value!!.id)?.phase)
    }
    @Test fun keepingAiProposalCreatesIndependentCopyAndPreservesConcurrentLocalEdits() = runTest {
        val original = projects.create("Original")
        val changed = projects.apply(original.id, original.revision, StudioCommand.Background(Color.BLUE))
        val result = com.halo.sdk.provider.AiImageResult(png(), "image/png", "fixture-provider", "fixture-model")
        val kept = service.keepAiCopy(original, result, "Proposal copy")
        assertNotEquals(original.id, kept.id)
        assertEquals(changed, projects.get(original.id))
        assertFalse(kept.hasUnsavedChanges)
        val source = kept.session.current.layers.single().source!!
        val provenance = db.workspaceContextDao().provenanceFor(source.objectId)!!
        assertEquals(original.id, provenance.sourceId)
        assertEquals(original.revision.toString(), provenance.revision)
        val metadata = org.json.JSONObject(provenance.metadata)
        assertEquals(original.session.current, StudioDocumentCodec.decode(metadata.getJSONObject("document").toString()))
        assertFalse(metadata.has("prompt"))
    }
    @Test fun cropRotateAndMirrorReuseCanonicalPixelTransform() = runTest {
        val source = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(source)
        canvas.drawColor(Color.BLUE)
        canvas.drawRect(0f, 0f, 50f, 60f, android.graphics.Paint().apply { color = Color.RED })
        val transformed = CanvasImageTools.transformOwnedBitmap(source,
            CanvasImageRecipe(quarterTurns = 1, mirrorHorizontal = true, crop = CanvasImageCrop(0, 0, 500, 1000)), false)
        try { assertEquals(60, transformed.width); assertEquals(50, transformed.height); assertEquals(Color.RED, transformed.getPixel(30, 25)) }
        finally { transformed.recycle() }
    }
    @Test fun previewIsBoundedAndMaskClipsPixelsWithoutChangingSourceDocument() = runTest {
        val document = StudioDocument(2000, 1000, background = Color.WHITE, layers = listOf(
            StudioLayer(name = "Masked", kind = StudioLayerKind.RECTANGLE, color = Color.BLACK,
                mask = StudioRect(0, 0, 500, 1000))))
        val image = renderer.render(document, true)
        try {
            assertTrue(image.width <= 640 && image.height <= 640)
            assertEquals(Color.BLACK, image.getPixel(image.width / 4, image.height / 2))
            assertEquals(Color.WHITE, image.getPixel(image.width * 3 / 4, image.height / 2))
        } finally { image.recycle() }
        assertEquals(2000, document.width)
        assertEquals(StudioRect(0, 0, 500, 1000), document.layers.single().mask)
    }
    @Test fun changedOrTrashedImageFailsClosedAndManagedPublicationRollsBack() = runTest {
        val created = projects.create("Source")
        val input = files.createManagedFile("original.png", "image/png", png(), created.id, "1", "{}")
        readable(input)
        val imported = service.importImage(created.id, created.revision, input.objectId.value)
        db.notebookDao().setLifecycle(input.objectId.value, "TRASHED", 10)
        assertTrue(runCatching { renderer.render(imported.session.current, true).recycle() }.isFailure)
        val before = db.workspaceFileDao().allFiles()
        assertTrue(runCatching {
            files.createManagedFile("rejected.png", "image/png", png(), created.id, "1", "{}") { error("Stale source") }
        }.isFailure)
        assertEquals(before, db.workspaceFileDao().allFiles())
        assertEquals(1, root.walkTopDown().count { it.isFile })
    }
    @Test fun legacyOwnedCopyGetsVerifiedHashAndCorruptOwnedCopyIsNotReimported() = runTest {
        val created = projects.create("Source")
        val bytes = png()
        val input = files.createManagedFile("original.png", "image/png", bytes, created.id, "1", "{}")
        val row = db.workspaceFileDao().getFile(input.objectId.value)!!
        db.workspaceFileDao().insertFile(row.copy(sha256 = null))
        val promoted = files.keepLocalCopyBounded(input.objectId, 20_000_000)
        assertEquals(input.sha256, promoted.sha256)
        File(row.ownedCopyPath!!).writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { files.keepLocalCopyBounded(input.objectId, 20_000_000) }.isFailure)
        assertEquals(input.sha256, files.getFile(input.objectId)?.sha256)
        assertEquals(1, root.walkTopDown().count { it.isFile })
    }
    @Test fun staleAvailabilityObservationCannotOverwriteAnOwnedPromotion() = runTest {
        val created = projects.create("Source")
        val input = files.createManagedFile("original.png", "image/png", png(), created.id, "1", "{}")
        val row = db.workspaceFileDao().getFile(input.objectId.value)!!
        val changed = row.copy(uri = "content://new/source", sha256 = "new-sha", modifiedAt = 99)
        db.workspaceFileDao().insertFile(changed)
        assertEquals(0, db.workspaceFileDao().updateAvailabilityIfCurrent(row.objectId, row.uri, row.ownership,
            row.sha256, row.modifiedAt, row.permissionPersisted, WorkspaceFileAvailability.MISSING.name))
        assertEquals(changed, db.workspaceFileDao().getFile(input.objectId.value))
    }
}
