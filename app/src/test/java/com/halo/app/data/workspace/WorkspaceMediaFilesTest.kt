package com.halo.app.data.workspace

import android.graphics.BitmapFactory
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceMediaFilesTest {
    @Test fun rotationConversionAndCollagePublishNewFilesPreservingOriginals() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.png("seed-one")
            val second = f.png("seed-two")
            val original = f.bytes(first)
            val output = f.media.transform(f.reference(first), CanvasImageRecipe(quarterTurns = 1, format = CanvasImageFormat.JPEG))
            val encoded = f.bytes(output)
            val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            try { assertEquals(80, bitmap.width); assertEquals(160, bitmap.height) } finally { bitmap.recycle() }
            assertEquals("image/jpeg", output.mimeType)
            assertNotEquals(first.objectId, output.objectId)
            val collage = f.media.collage(listOf(f.reference(first), f.reference(second)), CanvasStitchRecipe())
            assertEquals("image/png", collage.mimeType)
            assertTrue(f.bytes(collage).isNotEmpty())
            assertArrayEquals(original, f.bytes(first))
        }
    }
    @Test fun partialBatchKeepsSavedOutputsAndReopeningNeverReplays() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.png("seed")
            val receipt = f.media.batch("Batch", listOf(f.reference(first), WorkspaceFileVersion("missing", "old")), CanvasImageRecipe())
            assertEquals(WorkspaceBatchPhase.PARTIAL, receipt.phase)
            assertEquals(listOf(WorkspaceBatchItemPhase.SAVED, WorkspaceBatchItemPhase.FAILED), receipt.items.map { it.phase })
            val saved = receipt.items.first().output!!
            assertNotNull(f.files.getFile(WorkspaceObjectId(saved.objectId)))
            val reopened = WorkspaceFileReceipts(f.database, f.notes, f.documents).get(receipt.id)
            assertEquals(receipt, reopened)
        }
    }
    @Test fun cancellationHasDurableReceiptAndInterruptedReceiptCannotPretendCompletion() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.png("seed")
            var receiptId: String? = null
            assertTrue(runCatching { f.media.batch("Cancelled", listOf(f.reference(first)), CanvasImageRecipe()) {
                receiptId = it.id
                throw CancellationException("fixture cancellation")
            } }.exceptionOrNull() is CancellationException)
            assertEquals(WorkspaceBatchPhase.CANCELLED, f.receipts.get(receiptId!!).phase)
            val abandoned = f.receipts.create("Interrupted", listOf(f.reference(first)))
            val reopened = WorkspaceFileReceipts(f.database, f.notes, f.documents).get(abandoned.id)
            assertEquals(WorkspaceBatchPhase.INTERRUPTED, reopened.phase)
            assertNull(reopened.items.single().output)
        }
    }
    @Test fun managedPublicationAndReceiptRollBackTogetherOnFailure() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.png("seed")
            val job = f.receipts.create("Atomic batch", listOf(f.reference(source)))
            var attempted: WorkspaceFile? = null
            val bytes = f.bytes(source)
            assertTrue(runCatching { f.files.createManagedFile("rolled-back.png", "image/png", bytes,
                source.objectId.value, source.version!!, "{}", published = { output ->
                    attempted = output
                    f.receipts.saved(job.id, 0, f.reference(source), output)
                    error("fixture rollback")
                }) }.isFailure)
            assertEquals(WorkspaceBatchItemPhase.PENDING, f.receipts.get(job.id).items.single().phase)
            assertNull(f.files.getFile(attempted!!.objectId))
            f.receipts.release(job.id)
        }
    }
    @Test fun staleSourceCannotProduceOutput() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.png("seed")
            assertTrue(runCatching { f.media.transform(WorkspaceFileVersion(source.objectId.value, "stale"), CanvasImageRecipe()) }.isFailure)
        }
    }
}
