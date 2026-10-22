package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.FilePermissionMode
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkSelectionBounds
import com.halo.sdk.runtime.ink.InkSelectionContext
import com.halo.sdk.runtime.ink.InkSelectionOrigin
import com.halo.sdk.runtime.ink.InkStroke
import com.halo.sdk.runtime.ink.InkTool
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasInkSelectionCropTest {
    @Test fun sketchCropContainsOnlySelectedStrokesAndClipsToSavedBounds() {
        val doc = InkDocument(strokes = listOf(
            InkStroke(InkTool.STYLUS, listOf(InkPoint(0, 500), InkPoint(1000, 500)), "selected"),
            InkStroke(InkTool.STYLUS, listOf(InkPoint(500, 0), InkPoint(500, 1000)), "not-selected"),
        ), aspectMilli = 1000)

        val bytes = CanvasInkSelectionCropRenderer.renderSketch(InkCodec.encode(doc), selection())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(768, bitmap.width)
            assertEquals(768, bitmap.height)
            assertTrue(Color.red(bitmap.getPixel(384, 384)) < 80)
            val outsideRed = Color.red(bitmap.getPixel(384, 40))
            assertTrue("crop should exclude marks beyond saved bounds: red=$outsideRed", outsideRed > 240)
        } finally { bitmap.recycle() }
    }

    @Test fun imagePageCropContainsSourcePixelsAndOnlySelectedPageInk() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "ink-selection-source.png")
        val sourceBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            sourceBitmap.eraseColor(Color.RED)
            FileOutputStream(source).use { check(sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { sourceBitmap.recycle() }
        val file = WorkspaceFile(WorkspaceObjectId("file-source"), Uri.fromFile(source), "source.png", "image/png",
            source.length(), FilePermissionMode.READ_ONLY, ContentOwnership.EXTERNAL_REFERENCE,
            WorkspaceFileAvailability.AVAILABLE, sha256 = null, version = "source-v1")
        val pageInk = InkCodec.encode(InkDocument(strokes = listOf(
            InkStroke(InkTool.STYLUS, listOf(InkPoint(0, 500), InkPoint(1000, 500)), "selected"),
            InkStroke(InkTool.STYLUS, listOf(InkPoint(500, 0), InkPoint(500, 1000)), "not-selected"),
        ), aspectMilli = 1000))

        val bytes = kotlinx.coroutines.runBlocking {
            CanvasInkSelectionCropRenderer.renderPage(context, file, 1, pageInk, selection("file-source"))
        }
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(50, result.width)
            assertEquals(50, result.height)
            assertTrue("crop should retain source pixels", Color.red(result.getPixel(2, 2)) > 240)
            val selectedRed = Color.red(result.getPixel(25, 25))
            assertTrue("crop should include selected ink: red=$selectedRed", selectedRed < 200)
            assertTrue("crop should omit unselected ink", Color.red(result.getPixel(25, 2)) > 240)
        } finally {
            result.recycle()
            source.delete()
        }
    }

    private fun selection(sourceId: String = "source") = InkSelectionContext(
        workspaceObjectId = sourceId,
        pageId = "page-1",
        bounds = InkSelectionBounds(250, 250, 750, 750),
        selectedStrokeIds = listOf("selected"),
        sourceRevision = "revision-1",
        origin = InkSelectionOrigin.BOX,
        createdAtMs = 1,
        updatedAtMs = 1,
    )
}
