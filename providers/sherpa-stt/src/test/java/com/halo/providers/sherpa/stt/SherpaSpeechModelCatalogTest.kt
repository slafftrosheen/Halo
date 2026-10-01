package com.halo.providers.sherpa.stt

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaSpeechModelCatalogTest {
    @Test
    fun incompleteWhisperModelIsNotInstalled() {
        val directory = createTempDir(prefix = "halo-sherpa-")
        try {
            File(directory, "tiny-encoder.int8.onnx").writeBytes(ByteArray(10_000_001))
            assertFalse(
                SherpaSpeechModelCatalog.isInstalled(
                    directory,
                    SherpaSpeechModelCatalog.whisperTiny
                )
            )
            assertTrue(
                SherpaSpeechModelCatalog.missingFiles(
                    directory,
                    SherpaSpeechModelCatalog.whisperTiny
                ).contains("tiny-decoder.int8.onnx")
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun completeWhisperModelPassesValidation() {
        val directory = createTempDir(prefix = "halo-sherpa-")
        try {
            SherpaSpeechModelCatalog.whisperTiny.files.forEach { spec ->
                java.io.RandomAccessFile(File(directory, spec.name), "rw").use { it.setLength(spec.expectedBytes) }
            }
            assertTrue(
                SherpaSpeechModelCatalog.isInstalled(
                    directory,
                    SherpaSpeechModelCatalog.whisperTiny
                )
            )
            assertTrue(
                SherpaSpeechModelCatalog.missingFiles(
                    directory,
                    SherpaSpeechModelCatalog.whisperTiny
                ).isEmpty()
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
