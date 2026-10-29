package com.halo.app.ui.workspace

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class StudioPreviewLifetimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publishedPreviewRemainsDrawableAcrossDocumentChangesAndDisposal() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "preview-lifetime-${UUID.randomUUID()}").also { it.mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val files = RoomWorkspaceFileRepository(context, db, root, ownedUri = Uri::fromFile)
        val workspace = RoomWorkspaceRepository(db.notebookDao())
        val service = StudioService(context, StudioProjectRepository(db), files, workspace,
            StudioRenderer(context, files, workspace), NotebookAssetRepository(context, db),
            NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()),
            db.workspaceContextDao(), StudioUiRequests(scope, {}))
        val document = mutableStateOf(StudioDocument(320, 160))
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                HostlessHaloTheme {
                    if (visible.value) StudioDocumentPreview(service, document.value, Modifier.size(160.dp))
                }
            }
            repeat(3) { revision ->
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithContentDescription("halo Studio").fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue(compose.onNodeWithTag("studio-preview").captureToImage().width > 0)
                compose.runOnIdle {
                    document.value = document.value.copy(background = if (revision % 2 == 0) 0xff126754.toInt() else 0xffd6e9e2.toInt())
                }
            }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            scope.cancel()
            db.close()
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }
}
