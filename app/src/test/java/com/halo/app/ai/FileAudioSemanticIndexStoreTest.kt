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
class FileAudioSemanticIndexStoreTest {
    private val sha = "a".repeat(64)
    private val space = EmbeddingSpace(sha,2,true,"litertlm/0.18.0",
        "embeddinggemma2-mono-16khz-audio-cross-modal-search-v1")

    @Test fun audioIndexRoundTripsVectorsWithoutAudioPayloads() = runBlocking {
        val directory = Files.createTempDirectory("halo-audio-index").toFile()
        val file = directory.resolve("audio-index.json")
        val store = FileAudioSemanticIndexStore(file)
        val entry = AudioSemanticEntry("attachment:note1:audio1",sha,sha,
            "Notebook · memo","Notebook audio:note1",listOf(1f,0f))
        try {
            store.replace(AudioSemanticSnapshot(space,listOf(entry)))
            assertEquals(AudioSemanticSnapshot(space,listOf(entry)),store.read())
            val persisted = file.readText()
            assertTrue(persisted.contains("AUDIO"))
            assertFalse(persisted.contains("original audio bytes"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun audioStoreRejectsImageAndTextEmbeddingSpaces() = runBlocking {
        val directory = Files.createTempDirectory("halo-audio-index").toFile()
        try {
            val store = FileAudioSemanticIndexStore(directory.resolve("audio-index.json"))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.replace(AudioSemanticSnapshot(space.copy(
                    preprocessingIdentity="embeddinggemma2-full-image-cross-modal-search-v1"),emptyList())) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.replace(AudioSemanticSnapshot(space.copy(
                    preprocessingIdentity="text-special-tokens-v1"),emptyList())) }
            }
        } finally { directory.deleteRecursively() }
    }
}
