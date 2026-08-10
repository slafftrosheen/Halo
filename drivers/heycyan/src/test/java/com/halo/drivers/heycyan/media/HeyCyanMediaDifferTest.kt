package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultHeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaType
import com.halo.drivers.heycyan.media.repository.StoredHeyCyanMedia
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaDiffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanMediaDifferTest {

    private val resolver = DefaultHeyCyanMediaIdentityResolver()
    private val differ = HeyCyanMediaDiffer(resolver)

    @Test
    fun emptyRemoteManifestProducesEmptyDiff() {
        val diff = differ.diff(remote = emptyList(), local = emptyList())
        assertTrue(diff.newItems.isEmpty())
        assertTrue(diff.alreadyPresent.isEmpty())
        assertTrue(diff.changedItems.isEmpty())
        assertTrue(diff.localOnly.isEmpty())
    }

    @Test
    fun allMediaAlreadyDownloaded() {
        val photo = HeyCyanMedia.Photo("photo1.jpg", "photo1.jpg", 1000L, 100L, null, null)
        val id = resolver.identity(photo)
        val local = StoredHeyCyanMedia(
            identity = id,
            deviceId = null,
            remoteId = photo.id,
            filename = photo.filename,
            mediaType = HeyCyanMediaType.PHOTO,
            localPath = "/tmp/photo1.jpg",
            sizeBytes = 1000L,
            createdAtMillis = 100L,
            downloadedAtMillis = 200L,
            sha256 = null,
        )

        val diff = differ.diff(remote = listOf(photo), local = listOf(local))
        assertTrue(diff.newItems.isEmpty())
        assertEquals(1, diff.alreadyPresent.size)
        assertTrue(diff.changedItems.isEmpty())
    }

    @Test
    fun identifiesNewPhotoVideoAudio() {
        val photo = HeyCyanMedia.Photo("photo1.jpg", "photo1.jpg", 1000L, 100L, null, null)
        val video = HeyCyanMedia.Video("video1.mp4", "video1.mp4", 5000L, 100L, null)
        val audio = HeyCyanMedia.Audio("rec1.opus", "rec1.opus", 2000L, 100L, null)

        val diff = differ.diff(remote = listOf(photo, video, audio), local = emptyList())
        assertEquals(3, diff.newItems.size)
        assertTrue(diff.alreadyPresent.isEmpty())
    }

    @Test
    fun identifiesChangedMediaWhenSizeBytesDiffers() {
        val photo = HeyCyanMedia.Photo("photo1.jpg", "photo1.jpg", 2000L, 100L, null, null)
        val initialPhoto = HeyCyanMedia.Photo("photo1.jpg", "photo1.jpg", 1000L, 100L, null, null)
        val id = resolver.identity(initialPhoto)
        val local = StoredHeyCyanMedia(
            identity = id,
            deviceId = null,
            remoteId = photo.id,
            filename = photo.filename,
            mediaType = HeyCyanMediaType.PHOTO,
            localPath = "/tmp/photo1.jpg",
            sizeBytes = 1000L,
            createdAtMillis = 100L,
            downloadedAtMillis = 200L,
            sha256 = null,
        )

        val diff = differ.diff(remote = listOf(photo), local = listOf(local))
        assertEquals(1, diff.changedItems.size)
        assertTrue(diff.newItems.isEmpty())
    }

    @Test
    fun identifiesLocalOnlyMedia() {
        val local = StoredHeyCyanMedia(
            identity = "old_id",
            deviceId = null,
            remoteId = "old.jpg",
            filename = "old.jpg",
            mediaType = HeyCyanMediaType.PHOTO,
            localPath = "/tmp/old.jpg",
            sizeBytes = 1000L,
            createdAtMillis = 100L,
            downloadedAtMillis = 200L,
            sha256 = null,
        )

        val diff = differ.diff(remote = emptyList(), local = listOf(local))
        assertEquals(1, diff.localOnly.size)
        assertEquals("old_id", diff.localOnly.first().identity)
    }
}
