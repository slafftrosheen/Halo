package com.halo.app.ai

import com.halo.sdk.provider.EmbeddingSpace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FileVideoSemanticIndexStoreTest {
    private val sha = "a".repeat(64)
    private val space = EmbeddingSpace(sha,2,true,"litertlm/0.18.0",
        com.halo.providers.litert.LocalMediaEmbeddingSafety.CROSS_MODAL_VIDEO_FRAME_SEARCH_V1)

    @Test fun videoIndexRoundTripsFrameVectorsWithoutVideoOrFramePayloads() = runBlocking {
        val directory = Files.createTempDirectory("halo-video-index").toFile()
        val file = directory.resolve("video-index.json")
        val store = FileVideoSemanticIndexStore(file)
        val entry = VideoSemanticEntry("attachment:note1:video1",sha,sha,
            "Notebook · clip","Notebook video frames:note1",listOf(0.6f,0.8f))
        try {
            store.replace(VideoSemanticSnapshot(space,listOf(entry)))
            assertEquals(VideoSemanticSnapshot(space,listOf(entry)),store.read())
            val persisted = file.readText()
            assertTrue(persisted.contains("VIDEO_FRAME"))
            assertFalse(persisted.contains("original video bytes"))
            assertFalse(persisted.contains("sampled frame bytes"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun videoIndexRejectsImageAudioAndTextSpaces() = runBlocking {
        val directory = Files.createTempDirectory("halo-video-index").toFile()
        try {
            val store = FileVideoSemanticIndexStore(directory.resolve("video-index.json"))
            for (identity in listOf("embeddinggemma2-full-image-cross-modal-search-v1",
                "embeddinggemma2-mono-16khz-audio-cross-modal-search-v1","text-special-tokens-v1")) {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { store.replace(VideoSemanticSnapshot(space.copy(preprocessingIdentity=identity),emptyList())) }
                }
            }
        } finally { directory.deleteRecursively() }
    }
}
