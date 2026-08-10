package com.halo.drivers.heycyan.media

import org.junit.Assert.assertEquals
import org.junit.Test

class HeyCyanMediaIdentityResolverTest {

    private val resolver: MediaIdentityResolver = DefaultMediaIdentityResolver()

    @Test
    fun identityWithFilenameOnly() {
        val media = HeyCyanMedia.Photo(
            id = "photo1.jpg",
            filename = "photo1.jpg",
            sizeBytes = null,
            createdAt = null,
            width = null,
            height = null,
        )
        assertEquals("photo1.jpg", resolver.identity(media))
    }

    @Test
    fun identityWithMetadataCompositeKey() {
        val media = HeyCyanMedia.Photo(
            id = "photo1.jpg",
            filename = "photo1.jpg",
            sizeBytes = 1024L,
            createdAt = 1600000000000L,
            width = null,
            height = null,
        )
        assertEquals("photo1.jpg:1024:1600000000000", resolver.identity(media))
    }
}
