package com.halo.providers.sherpa.stt

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelInventoryRemovalTest {
    @Test
    fun remainingBytesCountsOnlyMissingBytesFromPinnedPartial() {
        val root = createTempDir(prefix = "halo-silero-budget-")
        try {
            val spec = SherpaSpeechModelCatalog.sileroVad.files.single()
            val partial = File(root, "${spec.name}.${spec.sha256}.download")
            partial.writeBytes(ByteArray(100_000))

            assertEquals(spec.expectedBytes - 100_000L,
                SpeechModelInstaller.remainingBytes(SherpaSpeechModelCatalog.sileroVad, root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removingSileroReleasesRuntimeAndDeletesOnlyItsFiles() = runBlocking {
        val root = createTempDir(prefix = "halo-silero-remove-")
        try {
            val vad = File(root, SherpaSpeechModelCatalog.SILERO_VAD_FILE).apply { writeText("model") }
            val proof = File(root, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.verified").apply { writeText("proof") }
            val partial = File(root, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.partial").apply { writeText("partial") }
            val unrelated = File(root, "keep.txt").apply { writeText("keep") }
            var released = false
            val repository = DefaultSpeechModelInventoryRepository(
                speechRoot = { root },
                releaseSileroVad = { released = true }
            )

            val result = repository.remove(SherpaSpeechModelCatalog.SILERO_VAD_ID)

            assertTrue(result.isSuccess)
            assertTrue(released)
            assertFalse(vad.exists())
            assertFalse(proof.exists())
            assertFalse(partial.exists())
            assertTrue(unrelated.exists())
            assertFalse(repository.state.value.sileroInstalled)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removingUnknownSpeechModelFailsWithoutDeletingFiles() = runBlocking {
        val root = createTempDir(prefix = "halo-unknown-remove-")
        try {
            val unrelated = File(root, "keep.txt").apply { writeText("keep") }
            val repository = DefaultSpeechModelInventoryRepository(speechRoot = { root })

            val result = repository.remove("unknown-model")

            assertTrue(result.isFailure)
            assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
