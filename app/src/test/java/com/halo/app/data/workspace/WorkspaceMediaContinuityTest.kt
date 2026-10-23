package com.halo.app.data.workspace

import com.halo.app.capture.WorkspaceMediaAssetResolver
import com.halo.app.data.notebook.*
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceMediaContinuityTest {
    private fun reference(file: WorkspaceFile) = MediaAssetRef("workspace:${file.objectId.value}", MediaKind.IMAGE,
        file.mimeType, file.sizeBytes, file.sha256, 1, MediaProvenance.WORKSPACE,
        MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId = file.objectId.value)
    @Test fun mediaChecksLifecycleAndAvailabilityBeforeOpeningOwnedBytes() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val file = f.png("Photo")
            val media = WorkspaceMediaAssetResolver(f.context, f.files, noEphemeral(), objects = f.workspace)
            val ref = reference(file)
            assertNotNull(media.metadata(ref))
            assertTrue(media.open(ref).bytes.isNotEmpty())
            val row = f.database.workspaceFileDao().getFile(file.objectId.value)!!
            f.database.workspaceFileDao().insertFile(row.copy(availability = WorkspaceFileAvailability.NEEDS_RELINK.name))
            assertNull(media.metadata(ref))
            assertTrue(runCatching { media.open(ref) }.isFailure)
            f.database.workspaceFileDao().insertFile(row)
            f.workspace.trash(file.objectId)
            assertNull(media.metadata(ref))
            assertTrue(runCatching { media.open(ref) }.isFailure)
        }
    }
    @Test fun trashDuringByteReadDoesNotReturnContent() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val file = f.png("Photo")
            val bytes = f.bytes(file)
            Shadows.shadowOf(f.context.contentResolver).registerInputStreamSupplier(file.uri) {
                object : ByteArrayInputStream(bytes) {
                    var changed = false
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val count = super.read(buffer, offset, length)
                        if (!changed && count > 0) { changed = true; runBlocking { f.workspace.trash(file.objectId) } }
                        return count
                    }
                }
            }
            val media = WorkspaceMediaAssetResolver(f.context, f.files, noEphemeral(), objects = f.workspace)
            assertTrue(runCatching { media.open(reference(file)) }.exceptionOrNull() is MediaAcquisitionException)
        }
    }
    @Test fun actualBytesMustMatchTheBoundedPersistedImageReference() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val file = f.png("Photo")
            val media = WorkspaceMediaAssetResolver(f.context, f.files, noEphemeral(), objects = f.workspace)
            assertNull(media.metadata(reference(file).copy(sha256 = "stale")))
            assertNull(media.metadata(reference(file).copy(sha256 = null)))
            Shadows.shadowOf(f.context.contentResolver).registerInputStreamSupplier(file.uri) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            val failure = runCatching { media.open(reference(file)) }.exceptionOrNull() as MediaAcquisitionException
            assertEquals(MediaFailure.TRANSFER_FAILED, failure.failure)
        }
    }
    @Test fun sameClockRelinkUsesOneVersionAcrossFilesCanvasAndContext() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val file = f.png("External image")
            val stored = f.database.workspaceFileDao().getFile(file.objectId.value)!!
            val first = stored.copy(uri = "content://fixture/first", modifiedAt = 42, sha256 = null,
                ownership = ContentOwnership.EXTERNAL_REFERENCE.name, ownedCopyPath = null,
                permissionPersisted = true, availability = WorkspaceFileAvailability.AVAILABLE.name)
            f.database.workspaceFileDao().insertFile(first)
            val context = f.contextOwner()
            val observed = f.files.getFile(file.objectId)!!.version!!
            assertEquals(observed, context.versionFor(file.objectId.value))
            val canvas = f.canvases.create("Board")
            val node = f.canvases.addReference(canvas, file.objectId.value, 0f, 0f, image = true)
            f.database.workspaceFileDao().insertFile(first.copy(uri = "content://fixture/second"))
            val changed = f.files.getFile(file.objectId)!!.version!!
            assertNotEquals(observed, changed)
            assertEquals(changed, context.versionFor(file.objectId.value))
            assertTrue(runCatching { f.files.pinLocalCopy(file.objectId, observed, 20_000_000) }.isFailure)
            assertTrue(runCatching { f.canvases.saveFileMarkup(canvas, node, emptyMap(), expectedFileVersion = observed) }.isFailure)
            assertTrue(runCatching { f.studio().createFromImage("No stale copy", file.objectId.value, observed) }.isFailure)
            f.database.workspaceFileDao().insertFile(first.copy(availability = WorkspaceFileAvailability.MISSING.name))
            assertNull(context.versionFor(file.objectId.value))
        }
    }
}
