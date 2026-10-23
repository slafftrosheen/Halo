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
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HaloTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

/** Synthetic isolated database/cache only. No live graph, provider, profiles or user documents. */
@RunWith(AndroidJUnit4::class)
class StudioDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun localDocumentExportsAndReopensRealPngWithoutChangingItsOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "studio-device-" + UUID.randomUUID()).also { it.mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val projects = StudioProjectRepository(db)
            val files = RoomWorkspaceFileRepository(context, db, root, ownedUri = Uri::fromFile)
            val workspace = RoomWorkspaceRepository(db.notebookDao())
            val renderer = StudioRenderer(context, files, workspace)
            val service = StudioService(context, projects, files, workspace, renderer, NotebookAssetRepository(context, db),
                NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()), db.workspaceContextDao(), StudioUiRequests(scope, {}))
            val original = projects.create("Synthetic local artwork", StudioDocument(320, 160))
            val bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val bytes = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { bitmap.recycle() }
            val input = files.createManagedFile("original.png", "image/png", bytes, original.id, "1", "{}")
            val imported = service.importImage(original.id, original.revision, input.objectId.value)
            val resized = projects.apply(imported.id, imported.revision, StudioCommand.Resize(320, 160))
            val changed = projects.apply(resized.id, resized.revision, StudioCommand.Background(Color.BLUE))
            val output = service.export(changed.id, changed.revision, CanvasImageFormat.PNG)
            val reopened = files.getFile(output.objectId)!!
            val decoded = context.contentResolver.openInputStream(reopened.uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
            try {
                assertEquals(320, decoded.width); assertEquals(160, decoded.height)
                assertEquals(Color.BLUE, decoded.getPixel(5, 80)); assertEquals(Color.RED, decoded.getPixel(160, 80))
            } finally { decoded.recycle() }
            assertArrayEquals(bytes, File(input.uri.path!!).readBytes())
            assertEquals(changed, StudioProjectRepository(db).get(changed.id))
            repeat(8) { renderer.render(changed.session.current, true).recycle() }
            assertTrue(runCatching { service.export(original.id, original.revision, CanvasImageFormat.PNG) }.isFailure)
        } finally {
            scope.cancel(); db.close()
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile); root.deleteRecursively()
        }
    }
    @Test fun layerReviewKeepsApplyAndCloseReachableInRussianAt200Percent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        val localized = context.createConfigurationContext(config)
        var dismissed = false
        var applied: StudioLayer? = null
        val layer = StudioLayer(id = "fixture", name = "Synthetic layer", kind = StudioLayerKind.TEXT, text = "Local artwork")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config, LocalInspectionMode provides true) {
                HaloTheme(fontScale = 2f) { StudioLayerDialog(layer, { applied = it }, { dismissed = true }) }
            }
        }
        compose.onNodeWithText("Применить").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Закрыть").assertIsDisplayed().performClick()
        assertTrue(dismissed)
        assertNull(applied)
    }
}
