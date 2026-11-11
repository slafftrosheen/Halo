package com.halo.app.data.workspace

import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DeviceMediaImportTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Test fun ownedImportSurvivesOwnerRecreationAndNeverRestoresTrash() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0)
            val source = File(f.context.cacheDir, "device-source-${f.root.name}").apply { writeBytes(bytes) }
            try {
                val first = f.files.importDeviceMedia(source, "a2", "remote.jpg", "remote.jpg", hash(bytes), bytes.size.toLong()) { true }
                assertFalse(first.alreadyImported)
                val file = f.files.getFile(WorkspaceObjectId(first.objectId))!!
                assertEquals("image/jpeg", file.mimeType)
                assertEquals(WorkspaceFileAvailability.OWNED_LOCAL, file.availability)
                assertArrayEquals(bytes, f.bytes(file))
                f.workspace.trash(file.objectId)
                val recreated = RoomWorkspaceFileRepository(f.context, f.database, f.root, ownedUri = { android.net.Uri.fromFile(it) })
                val repeated = recreated.importDeviceMedia(source, "a2", "remote.jpg", "remote.jpg", hash(bytes), bytes.size.toLong()) { true }
                assertEquals(first.objectId, repeated.objectId)
                assertTrue(repeated.alreadyImported && repeated.inTrash)
                assertEquals(WorkspaceObjectLifecycle.TRASHED, f.workspace.get(file.objectId)!!.lifecycle)
                assertEquals(1, f.files.observeFiles().first().size)
                f.workspace.restore(file.objectId)
                assertArrayEquals(bytes, f.bytes(f.files.getFile(file.objectId)!!))
            } finally { source.delete() }
        }
    }
    @Test fun badDigestAndRevokedAccessCannotPublishAndUnknownRecordingsStayOriginal() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val bytes = byteArrayOf(1,2,3,4)
            val source = File(f.context.cacheDir, "device-source-${f.root.name}").apply { writeBytes(bytes) }
            try {
                assertTrue(runCatching { f.files.importDeviceMedia(source, "a2", "raw.opus", "raw.opus", "0".repeat(64), 4) { true } }.isFailure)
                assertTrue(runCatching { f.files.importDeviceMedia(source, "a2", "raw.opus", "raw.opus", hash(bytes), 4) { false } }.isFailure)
                assertTrue(f.files.observeFiles().first().isEmpty())
                assertTrue(f.root.walkTopDown().none { it.isFile })
                val saved = f.files.importDeviceMedia(source, "a2", "raw.opus", "raw.opus", hash(bytes), 4) { true }
                assertEquals("application/octet-stream", saved.mimeType)
                assertArrayEquals(bytes, f.bytes(f.files.getFile(WorkspaceObjectId(saved.objectId))!!))
            } finally { source.delete() }
        }
    }
    @Test fun changedRemoteBytesMakeNewObjectAndCorruptCopyIsRepairedWithoutChangingLifecycle() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = File(f.context.cacheDir, "device-source-${f.root.name}")
            try {
                val bytes = byteArrayOf(1,2,3)
                source.writeBytes(bytes)
                val first = f.files.importDeviceMedia(source, "a2", "same", "same.bin", hash(bytes), 3) { true }
                val id = WorkspaceObjectId(first.objectId)
                f.workspace.archive(id)
                val path = f.database.workspaceFileDao().getFile(first.objectId)!!.ownedCopyPath!!
                File(path).writeBytes(byteArrayOf(4,5,6)) // Same length is insufficient integrity evidence.
                val repaired = f.files.importDeviceMedia(source, "a2", "same", "same.bin", hash(bytes), 3) { true }
                assertEquals(first.objectId, repaired.objectId)
                assertFalse(repaired.inTrash)
                assertEquals(WorkspaceObjectLifecycle.ARCHIVED, f.workspace.get(id)!!.lifecycle)
                assertArrayEquals(bytes, f.bytes(f.files.getFile(id)!!))
                source.writeBytes(byteArrayOf(4,5,6))
                val changed = f.files.importDeviceMedia(source, "a2", "same", "same.bin", hash(source.readBytes()), 3) { true }
                assertNotEquals(first.objectId, changed.objectId)
            } finally { source.delete() }
        }
    }
}
