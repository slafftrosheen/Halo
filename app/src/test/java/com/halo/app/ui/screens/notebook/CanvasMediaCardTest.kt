package com.halo.app.ui.screens.notebook

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.data.workspace.*
import com.halo.app.data.notebook.WorkspaceObjectId
import android.net.Uri
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
class CanvasMediaCardTest {
    @get:Rule val compose = createComposeRule()

    private fun source(width: Int = 640, height: Int = 480): Pair<File, NoteAttachment> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(60, 160, 120))
        val pixels = android.graphics.Canvas(image)
        val paint = android.graphics.Paint()
        paint.color = Color.RED; pixels.drawRect(0f, 0f, width / 6f, height.toFloat(), paint)
        paint.color = Color.BLUE; pixels.drawRect(width * 5 / 6f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.YELLOW; pixels.drawRect(0f, 0f, width.toFloat(), height / 6f, paint)
        paint.color = Color.CYAN; pixels.drawRect(0f, height * 5 / 6f, width.toFloat(), height.toFloat(), paint)
        val file = File.createTempFile("canvas-preview-", ".png", context.cacheDir)
        try { file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { image.recycle() }
        return file to NoteAttachment("attachment", "note", "asset", 0, null, "IMAGE", "image/png",
            "MANAGED_FILE", file.absolutePath, true, "Local photo", file.length(), width, height, null)
    }

    private fun awaitImage() = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription("Local photo").fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun bodySelectsAndOnlyExplicitInspectionOpensZoomWithoutChangingSource() {
        val (file, attachment) = source()
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        var selections = 0; var opens = 0
        try {
            compose.setContent {
                HostlessHaloTheme {
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.size(300.dp, 240.dp).testTag("media-card")) {
                            CanvasMediaCard("Local photo", attachment, null, "", true, true,
                                { selections++ }, { opens++ })
                        }
                    }
                }
            }
            awaitImage()
            val card = compose.onNodeWithTag("media-card").fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("canvas-media-preview").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.height / card.height >= .70f)
            compose.onNodeWithTag("canvas-media-preview").performClick()
            assertEquals(1, selections)
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithContentDescription("View preview").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Local photo") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(isDialog()).assertIsDisplayed().captureRoboImage()
            assertEquals(2, selections)
            compose.onNodeWithContentDescription("Zoom in").performClick()
            compose.onNodeWithTag("canvas-inspection-viewport").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 150%"))
            compose.onNodeWithContentDescription("Fit preview").performClick()
            compose.onNodeWithTag("canvas-inspection-viewport").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
            compose.onNodeWithContentDescription("Close").performClick()
            assertEquals(0, opens)
            assertArrayEquals(digest, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }

    @Test fun tallPhotoUsesMostOfTheCardViewportInDark200() = capture(40, 800, true, 2f)
    @Test fun widePhotoKeepsItsAspectWithoutCropping() = capture(800, 40, false, 1f)
    @Test @Config(qualifiers = "lv-rLV-w412dp-h850dp") fun latvianCard200() = capture(640, 480, true, 2f)

    private fun capture(width: Int, height: Int, dark: Boolean, scale: Float) {
        val (file, attachment) = source(width, height)
        try {
            compose.setContent {
                HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.size(320.dp, 260.dp).testTag("media-card")) {
                            CanvasMediaCard("Local photo", attachment, null, "", true, true, {}, {})
                        }
                    }
                }
            }
            awaitImage()
            val card = compose.onNodeWithTag("media-card").fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("canvas-media-preview").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.height / card.height >= .70f)
            compose.onNodeWithTag("media-card").captureRoboImage()
        } finally { file.delete() }
    }

    @Test fun missingCanonicalFileCannotFallBackToAnOldAttachment() {
        val (file, attachment) = source()
        try {
            compose.setContent {
                HostlessHaloTheme {
                    Box(Modifier.size(300.dp, 240.dp)) {
                        val missing = WorkspaceFile(WorkspaceObjectId("file"), Uri.fromFile(file), "Local photo", "image/png",
                            file.length(), FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED,
                            WorkspaceFileAvailability.MISSING, "old-version")
                        CanvasMediaCard("Local photo", attachment, missing, "", true, true, {}, {})
                    }
                }
            }
            compose.onNodeWithText("Source unavailable").assertExists()
            compose.onNodeWithContentDescription("Local photo").assertDoesNotExist()
            compose.onNodeWithContentDescription("View preview").assertIsNotEnabled()
        } finally { file.delete() }
    }

    @Test fun replacingTheSourceClosesItsOldInspection() {
        val (file, attachment) = source()
        val source = mutableStateOf(attachment)
        try {
            compose.setContent {
                HostlessHaloTheme {
                    Box(Modifier.size(300.dp, 240.dp)) {
                        CanvasMediaCard("Local photo", source.value, null, "", true, true, {}, {})
                    }
                }
            }
            awaitImage()
            compose.onNodeWithContentDescription("View preview").performClick()
            compose.onNode(isDialog()).assertExists()
            compose.runOnIdle { source.value = attachment.copy(assetId = "replacement") }
            compose.onNode(isDialog()).assertDoesNotExist()
        } finally { file.delete() }
    }

    @Test fun deferredCardDoesNotDecodeUntilExplicitlySelectedAndDraggingDoesNotInspect() {
        val (file, attachment) = source()
        val admitted = mutableStateOf(false)
        var drags = 0; var opens = 0
        try {
            compose.setContent {
                HostlessHaloTheme {
                    Box(Modifier.size(300.dp, 240.dp).pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(onDragStart = { drags++ }) { change, _ -> change.consume() }
                    }) {
                        CanvasMediaCard("Local photo", attachment, null, "", admitted.value, true,
                            { admitted.value = true }, { opens++ })
                    }
                }
            }
            compose.onNodeWithContentDescription("Local photo").assertDoesNotExist()
            compose.onNodeWithTag("canvas-media-preview").performTouchInput {
                down(center); advanceEventTime(700); moveBy(Offset(50f, 20f)); up()
            }
            assertEquals(1, drags)
            compose.onNode(isDialog()).assertDoesNotExist()
            assertEquals(0, opens)
            compose.onNodeWithTag("canvas-media-preview").performClick()
            awaitImage()
        } finally { file.delete() }
    }
}
