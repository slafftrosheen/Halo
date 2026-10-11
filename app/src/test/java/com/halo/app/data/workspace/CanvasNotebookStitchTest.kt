package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CanvasNotebookStitchTest {
    private fun ownedImage(context: Context, id: String, color: Int): CanvasStitchSource.NoteImage {
        val file = File(context.cacheDir, "stitch-$id.png")
        val bmp = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        try {
            bmp.eraseColor(color)
            file.outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bmp.recycle() }
        return CanvasStitchSource.NoteImage(NoteAttachment(
            id = id, noteId = "note-$id", assetId = "asset-$id", position = 0,
            caption = null, type = "IMAGE", mimeType = "image/png",
            storageType = "MANAGED_FILE", pathOrUri = file.absolutePath,
            permissionPersisted = true, displayName = id, bytes = file.length(),
            width = 12, height = 16, durationMs = null,
        ))
    }

    @Test fun managedNotebookImagesAreComposedWithoutReplacingSources() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = ownedImage(context, "red", Color.RED)
        val second = ownedImage(context, "blue", Color.BLUE)
        val result = CanvasImageStitcher.preview(context, listOf(first, second),
            CanvasStitchRecipe(mode = CanvasStitchMode.HORIZONTAL, gap = 0))
        try {
            assertEquals(24, result.width)
            assertEquals(16, result.height)
            assertEquals(Color.RED, result.getPixel(4, 4))
            assertEquals(Color.BLUE, result.getPixel(17, 4))
        } finally { result.recycle() }
        assertTrue(File(first.attachment.pathOrUri).exists())
        assertTrue(File(second.attachment.pathOrUri).exists())
    }

    @Test fun twoDifferentAttachmentsInOneNoteCanCompose() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val red = ownedImage(context, "one", Color.RED)
        val green = ownedImage(context, "two", Color.GREEN).let { source ->
            CanvasStitchSource.NoteImage(source.attachment.copy(noteId = red.ownerObjectId))
        }
        assertEquals(red.ownerObjectId, green.ownerObjectId)
        assertNotEquals(red.sourceKey, green.sourceKey)
        val bitmap = CanvasImageStitcher.preview(context, listOf(red, green),
            CanvasStitchRecipe(mode = CanvasStitchMode.HORIZONTAL, gap = 0))
        try {
            assertEquals(Color.RED, bitmap.getPixel(3, 3))
            assertEquals(Color.GREEN, bitmap.getPixel(18, 3))
        } finally { bitmap.recycle() }
    }

    @Test fun repeatedNotebookAssetCannotBeCombinedWithItself() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val same = ownedImage(context, "repeat", Color.GREEN)
        try {
            CanvasImageStitcher.preview(context, listOf(same, same), CanvasStitchRecipe()).recycle()
            fail("Duplicate source should be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
