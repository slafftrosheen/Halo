package com.halo.app.ui.workspace

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.*
import com.halo.app.capture.WorkspaceMediaAssetResolver
import com.halo.app.study.*
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

/** Isolated synthetic owners. No providers, user documents or OS preference writes. */
@RunWith(AndroidJUnit4::class)
class WorkspaceContinuityDeviceTest {
    @get:Rule val compose = createComposeRule()
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
    @Test fun nativePhotoStudioCanvasStudyAndChatReopenTheSameSavedResult() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "continuity-device-" + UUID.randomUUID()).also { it.mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val documents = NotebookDocumentRepository(database.notebookDocumentDao(), database)
            val notes = RoomNotebookRepository(database.notebookDao(), documents = documents)
            val workspace = RoomWorkspaceRepository(database.notebookDao())
            val files = RoomWorkspaceFileRepository(context, database, root, ownedUri = Uri::fromFile)
            val assets = NotebookAssetRepository(context, database)
            val canvases = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
            val versions = WorkspaceFileVersions(files, workspace)
            val receipts = WorkspaceFileReceipts(database, notes, documents)
            val text = WorkspaceTextDocuments(context, database, notes, documents, files, workspace)
            val fileTools = WorkspaceFileTools(database, text, WorkspaceMediaFiles(context, files, versions, receipts),
                WorkspaceFileAnnotations(context, database, notes, documents, files, versions), receipts, files, workspace, versions,
                documents, canvases, WorkspaceFileToolsRequests(scope, {}))
            val owner = WorkspaceContinuity(database, WorkspaceContextStore(database, notes, MemoryStorage()), files, fileTools, WorkspaceHandoffRequests(scope, {}))
            val projects = StudioProjectRepository(database)
            val studio = StudioService(context, projects, files, workspace, StudioRenderer(context, files, workspace), assets, canvases,
                database.workspaceContextDao(), StudioUiRequests(scope, {}))
            val note = notes.createNote(title = "Synthetic original")
            val bitmap = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val bytes = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { bitmap.recycle() }
            val source = files.createManagedFile("synthetic.png", "image/png", bytes, note.id, note.revision.toString(), "{}")
            val token = UUID.randomUUID().toString()
            val project = studio.createFromImage("Local continuity", source.objectId.value, source.version!!, token)
            val changed = projects.apply(project.id, project.revision, StudioCommand.Background(Color.BLUE))
            val saved = projects.save(project.id, changed.revision)
            assertEquals(saved, StudioProjectRepository(database).get(saved.id))
            assertEquals(saved.id, studio.createFromImage("Local continuity", source.objectId.value, source.version!!, token).id)
            val output = studio.export(saved.id, saved.revision, CanvasImageFormat.PNG)
            val canvas = canvases.create("Synthetic board")
            val destination = owner.canvases.first().single { it.objectId == canvas }
            val node = owner.insert(canvas, destination.version, output.objectId.value, output.version!!)
            assertEquals(output.objectId.value, canvases.observe(canvas).first()!!.nodes.single { it.id == node }.refObjectId)
            val pdf = object : StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("No PDF in this fixture")
                override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = error("No PDF in this fixture")
            }
            val material = WorkspaceStudySourceResolver(context, documents, workspace, files, pdf).resolveExplicitReference(output.objectId.value, "") as ResolvedStudySource.Visual
            val ephemeral = object : MediaAssetResolver {
                override suspend fun metadata(ref: MediaAssetRef): MediaAssetMetadata? = null
                override suspend fun open(ref: MediaAssetRef): MediaAssetContent = error("No ephemeral data")
            }
            val media = WorkspaceMediaAssetResolver(context, files, ephemeral, assets, workspace)
            assertArrayEquals(context.contentResolver.openInputStream(output.uri)!!.use { it.readBytes() }, media.open(material.asset).bytes)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(source.uri)!!.use { it.readBytes() })
            assertTrue(owner.describe(output.objectId.value).sources.any { it.objectId == source.objectId.value })
            val found = workspace.observe(WorkspaceQuery(search = "Local continuity")).first().map { it.id.value }
            assertTrue(saved.id in found && output.objectId.value in found)
            assertTrue(database.memoryRecordDao().observeActiveRecords().first().isEmpty())
            workspace.trash(source.objectId)
            assertTrue(runCatching { studio.createFromImage("No reuse", source.objectId.value, source.version!!) }.isFailure)
            assertEquals(WorkspaceSourceState.UNAVAILABLE, owner.describe(output.objectId.value).sources.single { it.objectId == source.objectId.value }.state)
        } finally {
            scope.cancel(); database.close()
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }
    @Test fun russianContinueDialogKeepsContextAndCloseReachableAt200Percent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        val localized = context.createConfigurationContext(config)
        var destination: WorkspaceDestination? = null
        var dismissed = false
        val item = WorkspaceContinuityItem("hidden-id", "hidden-version", "Synthetic artwork", WorkspaceObjectType.FILE,
            WorkspaceDestination.entries.toSet(), emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config, LocalInspectionMode provides true) {
                HaloTheme(fontScale = 2f) { WorkspaceContinueDialog(item, emptyList(), false, false, false, false,
                    { destination = it }, {}, {}, {}, {}, { dismissed = true }) }
            }
        }
        compose.onNodeWithText(localized.getString(com.halo.app.R.string.link53_done)).assertIsDisplayed()
        compose.onNodeWithText(localized.getString(com.halo.app.R.string.link53_chat)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(WorkspaceDestination.CHAT, destination)
        compose.onNodeWithText("hidden-id").assertDoesNotExist()
        compose.onNodeWithText(localized.getString(com.halo.app.R.string.link53_done)).performClick()
        assertTrue(dismissed)
    }
}
