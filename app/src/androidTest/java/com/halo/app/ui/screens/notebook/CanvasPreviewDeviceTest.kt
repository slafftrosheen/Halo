package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.ui.workspace.decodeWorkspacePreviewAsync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Actual Android decoders with owned synthetic cache files; never reads user documents. */
@RunWith(AndroidJUnit4::class)
class CanvasPreviewDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun largePhotoIsSampledAndRepeatedThumbnailsRemainBounded() = runBlocking {
        val file = File.createTempFile("issue50-photo-", ".jpg", context.cacheDir)
        val original = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.rgb(45, 150, 110))
        try { file.outputStream().use { check(original.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { original.recycle() }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        try {
            repeat(20) {
                val bitmap = requireNotNull(decodeWorkspacePreviewAsync(512) { file.inputStream() })
                try {
                    assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
                    assertTrue(bitmap.allocationByteCount <= 512 * 512 * 4)
                    assertEquals(2f, bitmap.width.toFloat() / bitmap.height, .01f)
                } finally { bitmap.recycle() }
            }
            val inspection = requireNotNull(decodeWorkspacePreviewAsync(1536) { file.inputStream() })
            try { assertTrue(inspection.width <= 1536 && inspection.height <= 1536) }
            finally { inspection.recycle() }
            assertArrayEquals(digest, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }

    @Test fun actualPdfRendererKeepsPagesSourceAndPixelBudget() = runBlocking {
        val file = File.createTempFile("issue50-pages-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            for ((index, color) in listOf(Color.RED, Color.GREEN).withIndex()) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
                page.canvas.drawColor(color); document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val source = WorkspaceFile(WorkspaceObjectId("issue50-synthetic"), Uri.fromFile(file), "Synthetic pages",
            "application/pdf", file.length(), FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED,
            WorkspaceFileAvailability.OWNED_LOCAL, "synthetic-v1")
        try {
            for ((number, color) in listOf(1 to Color.RED, 2 to Color.GREEN)) {
                val page = requireNotNull(renderInkPage(context, source, number, 512))
                try {
                    assertEquals(2, page.count)
                    assertEquals(number, page.page)
                    assertTrue(maxOf(page.bitmap.width, page.bitmap.height) <= 512)
                    assertEquals(color, page.bitmap.getPixel(page.bitmap.width / 2, page.bitmap.height / 2))
                } finally { page.bitmap.recycle() }
            }
            assertArrayEquals(digest, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }
}
