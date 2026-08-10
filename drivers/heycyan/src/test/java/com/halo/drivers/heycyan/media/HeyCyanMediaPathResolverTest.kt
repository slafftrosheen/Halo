package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultMediaPathResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanMediaPathResolverTest {

    private val resolver = DefaultMediaPathResolver(rootDir = "/app/storage", deviceId = "device_123")

    @Test
    fun sanitizesPathTraversalCharacters() {
        val evilMedia = HeyCyanMedia.Photo(
            id = "../../etc/passwd",
            filename = "../../etc/passwd",
            sizeBytes = 100L,
            createdAt = 100L,
            width = null,
            height = null,
        )

        val path = resolver.pathFor(evilMedia, "safe_identity")
        assertFalse("Path must not escape media root directory via ..", path.contains(".."))
        assertTrue("Path must reside in expected photo storage path", path.startsWith("/app/storage/device_123/photos/"))
    }

    @Test
    fun sanitizesWindowsPathTraversal() {
        val evilMedia = HeyCyanMedia.Video(
            id = "..\\..\\Windows\\System32\\cmd.exe",
            filename = "..\\..\\Windows\\System32\\cmd.exe",
            sizeBytes = 100L,
            createdAt = 100L,
            durationMs = null,
        )

        val path = resolver.pathFor(evilMedia, "safe_identity_2")
        assertFalse(path.contains(".."))
        assertTrue(path.startsWith("/app/storage/device_123/videos/"))
    }
}
