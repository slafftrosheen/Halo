package com.halo.drivers.heycyan.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanMediaConfigParserTest {

    private val parser = DefaultHeyCyanMediaConfigParser()

    @Test
    fun parsesSourceJsonObjectManifest() {
        val json = """{"files": ["photo1.jpg", "video1.mp4", "audio1.opus", "data.dat"]}"""
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(4, manifest.items.size)
    }

    @Test
    fun parsesSourceJsonArrayManifest() {
        val json = """["photo1.jpg", "video1.mp4"]"""
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(2, manifest.items.size)
    }

    @Test
    fun parsesLineBasedManifest() {
        val payload = """
            # Comment line
            photo1.jpg
            video1.mp4
        """.trimIndent()
        val result = parser.parse(payload)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(2, manifest.items.size)
    }

    @Test
    fun preservesMediaIds() {
        val json = """{"files": ["IMG_0001.JPG"]}"""
        val manifest = parser.parse(json).getOrThrow()
        val item = manifest.items.first()
        assertEquals("IMG_0001.JPG", item.id)
        assertEquals("IMG_0001.JPG", item.filename)
    }

    @Test
    fun identifiesPhoto() {
        val json = """{"files": ["test.jpg", "test.png", "test.heic"]}"""
        val manifest = parser.parse(json).getOrThrow()
        manifest.items.forEach { item ->
            assertTrue(item is HeyCyanMedia.Photo)
        }
    }

    @Test
    fun identifiesVideo() {
        val json = """{"files": ["test.mp4", "test.mov"]}"""
        val manifest = parser.parse(json).getOrThrow()
        manifest.items.forEach { item ->
            assertTrue(item is HeyCyanMedia.Video)
        }
    }

    @Test
    fun identifiesAudio() {
        val json = """{"files": ["rec.opus"]}"""
        val manifest = parser.parse(json).getOrThrow()
        val item = manifest.items.first()
        assertTrue(item is HeyCyanMedia.Audio)
    }

    @Test
    fun unknownMediaIsPreserved() {
        val json = """{"files": ["doc.bin"]}"""
        val manifest = parser.parse(json).getOrThrow()
        val item = manifest.items.first()
        assertTrue(item is HeyCyanMedia.Unknown)
        assertEquals("doc.bin", item.filename)
    }
}
