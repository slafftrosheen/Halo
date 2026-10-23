package com.halo.app.ui.screens.notebook

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class CanvasAnnotatedPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun descriptor(file: File, mime: String) = WorkspaceFile(WorkspaceObjectId("fixture"), Uri.fromFile(file),
        file.name, mime, file.length(), FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED,
        WorkspaceFileAvailability.OWNED_LOCAL, "fixture-v1")

    @Test fun imageAndLegacyInkUseExactlyTheSameFittedBounds() {
        val file = File.createTempFile("canvas-annotated-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(210, 230, 250))
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        org.robolectric.Shadows.shadowOf(context.contentResolver)
            .registerInputStreamSupplier(Uri.fromFile(file)) { file.inputStream() }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val markup = CanvasFileMarkupCodec.merge("Local diagram", mapOf(1 to "sketch:v1|0,0;1000,1000"))
        try {
            compose.setContent {
                HostlessHaloTheme {
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.size(320.dp, 240.dp).testTag("annotated-card")) {
                            val duplicate = NoteAttachment("same", "note", "asset", 0, null, "IMAGE", "image/png",
                                "MANAGED_FILE", file.absolutePath, true, "Duplicate source sentinel", file.length(), 640, 320, null)
                            CanvasMediaCard("Local diagram", duplicate, descriptor(file, "image/png"), markup,
                                true, true, {}, {})
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("canvas-source-fit", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithTag("canvas-source-fit", useUnmergedTree = true).assertCountEquals(1)
            compose.onNodeWithContentDescription("Duplicate source sentinel").assertDoesNotExist()
            val image = compose.onNodeWithTag("canvas-source-fit", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val ink = compose.onNodeWithTag("canvas-source-ink", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(image, ink)
            assertEquals(2f, image.width / image.height, .01f)
            compose.onNodeWithTag("annotated-card").captureRoboImage()
            assertArrayEquals(digest, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }

}
