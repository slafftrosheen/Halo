package com.halo.app.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [33])
class WorkspaceImagePreviewTest {
    @Test fun managedGeneratedFileCanBeReadAndLargeSourceIsSampled() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val source = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val file = File.createTempFile("image-preview-", ".png", context.cacheDir)
        try {
            file.writeBytes(bytes)
            var opens = 0
            val decoded = decodeWorkspacePreview { opens++; file.inputStream() }
            assertNotNull(decoded)
            assertEquals(1024, decoded!!.width)
            assertEquals(256, decoded.height)
            assertEquals(2, opens)
        } finally { file.delete(); source.recycle() }
    }

    @Test fun CanvasThumbnailAndExplicitInspectionUseSeparateBoundedAllocations() {
        val source = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        source.recycle()
        val original = encoded.copyOf()
        val thumbnail = decodeWorkspacePreview(512) { encoded.inputStream() }
        val inspection = decodeWorkspacePreview(Int.MAX_VALUE) { encoded.inputStream() }
        try {
            assertNotNull(thumbnail); assertNotNull(inspection)
            assertTrue(thumbnail!!.width <= 512 && thumbnail.height <= 512)
            assertTrue(thumbnail.allocationByteCount <= 512 * 512 * 4)
            assertTrue(inspection!!.width <= 1536 && inspection.height <= 1536)
            assertArrayEquals(original, encoded)
        } finally { thumbnail?.recycle(); inspection?.recycle() }
    }

    @Test fun invalidOrMissingImageHasAnExplicitFailedResult() {
        assertNull(decodeWorkspacePreview { "not an image".byteInputStream() })
        assertNull(decodeWorkspacePreview { null })
    }
}
