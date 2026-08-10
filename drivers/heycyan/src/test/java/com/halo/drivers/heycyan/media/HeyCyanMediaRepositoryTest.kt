package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultHeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.DefaultMediaPathResolver
import com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HeyCyanMediaRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun atomicCommitSavesFileAndMetadata() = runTest {
        val rootDir = tempFolder.newFolder("media_storage")
        val pathResolver = DefaultMediaPathResolver(rootDir.absolutePath, "test_device")
        val repository = FileHeyCyanMediaRepository(
            storageDir = rootDir,
            pathResolver = pathResolver,
            identityResolver = DefaultHeyCyanMediaIdentityResolver(),
        )

        val photo = HeyCyanMedia.Photo("sample.jpg", "sample.jpg", 100L, 1000L, null, null)
        val session = repository.beginWrite(photo)

        val sampleBytes = "BINARY_PHOTO_DATA_FIXTURE_CONTENT_12345".toByteArray()
        session.write(sampleBytes, 0, sampleBytes.size)

        val commitResult = repository.commit(session, sha256 = "testsha256")
        assertTrue(commitResult.isSuccess)

        val stored = commitResult.getOrThrow()
        assertEquals(photo.filename, stored.filename)
        assertEquals("testsha256", stored.sha256)

        // Verify stored file on disk matches binary fixture bytes
        val fileOnDisk = File(stored.localPath)
        assertTrue(fileOnDisk.exists())
        assertArrayEquals(sampleBytes, fileOnDisk.readBytes())

        // Verify record in repository list
        val list = repository.list()
        assertEquals(1, list.size)
        assertNotNull(repository.find(session.identity))
    }

    @Test
    fun abortRemovesTempFileAndLeavesNoMetadata() = runTest {
        val rootDir = tempFolder.newFolder("media_storage_abort")
        val pathResolver = DefaultMediaPathResolver(rootDir.absolutePath, "test_device")
        val repository = FileHeyCyanMediaRepository(
            storageDir = rootDir,
            pathResolver = pathResolver,
            identityResolver = DefaultHeyCyanMediaIdentityResolver(),
        )

        val photo = HeyCyanMedia.Photo("corrupt.jpg", "corrupt.jpg", 100L, 1000L, null, null)
        val session = repository.beginWrite(photo)
        val bytes = "PARTIAL_BYTES".toByteArray()
        session.write(bytes, 0, bytes.size)

        // Abort due to failure/cancellation
        repository.abort(session)

        // Verify no record stored
        val list = repository.list()
        assertTrue(list.isEmpty())
        assertNull(repository.find(session.identity))

        // Verify temp file does not exist
        val targetPath = pathResolver.pathFor(photo, session.identity)
        val tempFile = File(File(targetPath).parentFile, ".${File(targetPath).name}.part")
        assertFalse(tempFile.exists())
    }
}
