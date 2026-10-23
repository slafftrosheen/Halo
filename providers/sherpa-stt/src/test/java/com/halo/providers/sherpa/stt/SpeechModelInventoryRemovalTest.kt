package com.halo.providers.sherpa.stt

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelInventoryRemovalTest {
    @Test fun failedOwnedVadRemovalRestoresRuntimeAndRetainsChangedFile() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-vad-rollback-").toFile()
        try {
            val file = SherpaSpeechModelCatalog.sileroVad.files.single()
            val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
            val target = File(root, file.name).apply { writeText("changed") }
            File(root, file.name + ".acquired").writeText("$owner:${file.sha256}:${file.expectedBytes}")
            var releases = 0
            var reloads = 0
            val repository = DefaultSpeechModelInventoryRepository({ root },
                releaseSileroVad = { releases++ }, allowSileroVadReload = { reloads++ })
            assertTrue(repository.removeOwned(SherpaSpeechModelCatalog.SILERO_VAD_ID, owner).isFailure)
            assertEquals(1, releases)
            assertEquals(1, reloads)
            assertEquals("changed", target.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancelledOwnedVadReleaseRestoresRuntimeAndPropagatesCancellation() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-vad-cancel-").toFile()
        try {
            val file = SherpaSpeechModelCatalog.sileroVad.files.single()
            val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
            val target = File(root, file.name).apply { writeText("retained") }
            File(root, file.name + ".acquired").writeText("$owner:${file.sha256}:${file.expectedBytes}")
            var reloads = 0
            val repository = DefaultSpeechModelInventoryRepository({ root },
                releaseSileroVad = { throw kotlinx.coroutines.CancellationException("fixture") },
                allowSileroVadReload = { reloads++ })
            var cancelled = false
            try { repository.removeOwned(SherpaSpeechModelCatalog.SILERO_VAD_ID, owner) }
            catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(1, reloads)
            assertEquals("retained", target.readText())
        } finally { root.deleteRecursively() }
    }
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
