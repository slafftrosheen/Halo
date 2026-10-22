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
class FileImageSemanticIndexStoreTest {
    private val sha = "a".repeat(64)
    private val space = EmbeddingSpace(sha,2,true,"litertlm/0.18.0",
        "embeddinggemma2-full-image-cross-modal-search-v1")

    @Test fun imageIndexRoundTripsVectorsWithoutImagePayloads() = runBlocking {
        val directory = Files.createTempDirectory("halo-image-index").toFile()
        val file = directory.resolve("image-index.json")
        val store = FileImageSemanticIndexStore(file)
        val entries = listOf(
            ImageSemanticEntry("attachment:note1:asset1",sha,sha,
                "Notebook · diagram","Notebook picture:note1",listOf(1f,0f)),
            ImageSemanticEntry("canvas-image:board1:node1",sha,sha,
                "Canvas · diagram","Canvas image:board1:node1",listOf(0f,1f)),
        )
        try {
            store.replace(ImageSemanticSnapshot(space,entries))
            assertEquals(ImageSemanticSnapshot(space,entries),store.read())
            val persisted = file.readText()
            assertTrue(persisted.contains("IMAGE"))
            assertFalse(persisted.contains("original image bytes"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun imageStoreRejectsTextOnlyEmbeddingSpaces() = runBlocking {
        val directory = Files.createTempDirectory("halo-image-index").toFile()
        try {
            val store = FileImageSemanticIndexStore(directory.resolve("image-index.json"))
            val textSpace = space.copy(preprocessingIdentity="text-special-tokens-v1")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.replace(ImageSemanticSnapshot(textSpace,emptyList())) }
            }
        } finally { directory.deleteRecursively() }
    }
}
