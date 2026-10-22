package com.halo.providers.litert

import org.junit.Assert.*
import org.junit.Test

class LocalMediaEmbeddingSafetyTest {
    private val sha = "a".repeat(64)
    private val png = ByteArray(24).apply {
        this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4e; this[3] = 0x47
    }
    private val wav = ByteArray(44).apply {
        this[0] = 0x52; this[1] = 0x49; this[2] = 0x46; this[3] = 0x46
        this[8] = 0x57; this[9] = 0x41; this[10] = 0x56; this[11] = 0x45
    }

    @Test fun imageAndAudioRequireBoundedEncodings() {
        LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Image(png)))
        LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Audio(wav)))
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Image(ByteArray(25))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Audio(ByteArray(45))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,
                listOf(LocalEmbeddingInput.Image(ByteArray(LocalMediaEmbeddingSafety.MAX_IMAGE_BYTES+1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,
                listOf(LocalEmbeddingInput.Audio(ByteArray(LocalMediaEmbeddingSafety.MAX_AUDIO_BYTES+1))))
        }
    }

    @Test fun mediaRequestDoesNotSilentlyBecomeTextEmbeddingSpace() {
        assertEquals("embeddinggemma2-native-media-v1", LocalMediaEmbeddingRequest(
            sha,768,listOf(LocalEmbeddingInput.Image(png))).preprocessingIdentity)
        assertEquals(LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1, LocalMediaEmbeddingRequest(
            sha,768,listOf(LocalEmbeddingInput.Image(png)),
            LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1).preprocessingIdentity)
        assertEquals(LocalMediaEmbeddingSafety.CROSS_MODAL_AUDIO_SEARCH_V1, LocalMediaEmbeddingRequest(
            sha,768,listOf(LocalEmbeddingInput.Audio(wav)),
            LocalMediaEmbeddingSafety.CROSS_MODAL_AUDIO_SEARCH_V1).preprocessingIdentity)
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Text("hello")),
                preprocessingIdentity = "text-special-tokens-v1")
        }
    }

    @Test fun mediaRequestSnapshotsCallerOwnedCollectionsAndBytes() {
        val callerBytes = png.copyOf()
        val callerInputs = mutableListOf<LocalEmbeddingInput>(LocalEmbeddingInput.Image(callerBytes))
        val request = LocalMediaEmbeddingRequest(sha,768,callerInputs)

        callerBytes[0] = 0
        callerInputs.clear()

        assertArrayEquals(png, (request.inputs.single() as LocalEmbeddingInput.Image).encodedPngOrJpeg)
    }

    @Test fun mediaRequestDoesNotExposeItsValidatedByteSnapshot() {
        val request = LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Image(png)))
        val exposedBytes = (request.inputs.single() as LocalEmbeddingInput.Image).encodedPngOrJpeg
        exposedBytes[0] = 0

        assertArrayEquals(png, (request.inputs.single() as LocalEmbeddingInput.Image).encodedPngOrJpeg)
    }

    @Test fun batchAndIdentityCannotBeForged() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest("not-a-real-hash",768,listOf(LocalEmbeddingInput.Image(png)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,List(9) { LocalEmbeddingInput.Image(png) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMediaEmbeddingRequest(sha,768,listOf(LocalEmbeddingInput.Text("  ")))
        }
    }
}
