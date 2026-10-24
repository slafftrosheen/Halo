package com.halo.providers.sherpa.stt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpeechInventoryExactRemovalTest {
    @Test fun changedArtifactCannotBeRemovedByAnEarlierReviewedVersion() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-speech-exact-").toFile()
        try {
            val spec = SherpaSpeechModelCatalog.whisperTiny
            val directory = File(root, spec.directoryName).apply { mkdirs() }
            val file = File(directory, spec.files.first().name).apply { writeText("old partial artifact") }
            val owner = DefaultSpeechModelInventoryRepository({ root })
            val before = requireNotNull(owner.artifactVersion(spec.id))
            file.writeText("new independent artifact with a different size")
            assertNotEquals(before, owner.artifactVersion(spec.id))
            assertTrue(owner.removeExpected(spec.id, before).isFailure)
            assertTrue(file.exists())
            val current = requireNotNull(owner.artifactVersion(spec.id))
            assertTrue(owner.removeExpected(spec.id, current).isSuccess)
            assertFalse(directory.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun exactRemovalRetainsTheCanonicalSelectedModelGuard() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-speech-selected-").toFile()
        try {
            val spec = SherpaSpeechModelCatalog.whisperTiny
            val directory = File(root, spec.directoryName).apply { mkdirs() }
            val file = File(directory, spec.files.first().name).apply { writeText("selected artifact") }
            val owner = DefaultSpeechModelInventoryRepository({ root }, selectedProviderId = { spec.providerId })
            assertTrue(owner.removeExpected(spec.id, requireNotNull(owner.artifactVersion(spec.id))).isFailure)
            assertTrue(file.exists())
        } finally { root.deleteRecursively() }
    }
}
