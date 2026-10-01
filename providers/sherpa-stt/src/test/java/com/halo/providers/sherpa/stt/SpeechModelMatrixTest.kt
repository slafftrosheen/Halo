package com.halo.providers.sherpa.stt

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpeechModelMatrixTest {
    @Test fun catalogIsPinnedUniqueAndMultilingual() {
        val models = SherpaSpeechModelCatalog.recognitionModels
        assertEquals(4, models.size)
        assertEquals(4, models.map { it.id }.toSet().size)
        assertEquals(4, models.map { it.providerId }.toSet().size)
        assertEquals(4, models.map { it.directoryName }.toSet().size)
        models.forEach {
            assertTrue(it.revision.matches(Regex("[0-9a-f]{40}")))
            assertTrue(it.expectedBytes > 0)
            assertFalse(it.sourceUrl.contains("/main/"))
            assertTrue(it.files.all { file -> file.expectedBytes > 0 && file.sha256?.length == 64 })
            assertEquals(1, it.effectiveThreads(1))
            assertEquals(it.recommendedThreads, it.effectiveThreads(16))
        }
        models.filter { it.family == SpeechModelFamily.WHISPER }.forEach {
            assertTrue(it.multilingual); assertTrue(it.supportsLanguage("lv")); assertTrue(it.supportsLanguage("auto"))
        }
        assertFalse(SherpaSpeechModelCatalog.whisperBase.copy(supportedLanguageTags = setOf("en")).supportsLanguage("lv"))
    }
    @Test fun automaticPolicyAndExplicitMissingNeverFallback() {
        val tiny = SherpaSpeechModelCatalog.whisperTiny
        val base = SherpaSpeechModelCatalog.whisperBase
        val small = SherpaSpeechModelCatalog.whisperSmall
        assertEquals(tiny, SherpaSpeechModelCatalog.resolve(tiny.providerId) { it == tiny })
        assertEquals(base, SherpaSpeechModelCatalog.resolve(base.providerId) { it == base })
        assertNull(SherpaSpeechModelCatalog.resolve(small.providerId) { it == tiny })
        assertEquals(base, SherpaSpeechModelCatalog.resolve("AUTOMATIC") { true })
        assertEquals(tiny, SherpaSpeechModelCatalog.resolve("AUTOMATIC") { it == tiny })
        assertNull(SherpaSpeechModelCatalog.resolve("AUTOMATIC") { it == small })
    }
    @Test fun whisperExplicitLatvianAndAutomaticConfig() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            SherpaSpeechModelCatalog.recognitionModels.filter { it.family == SpeechModelFamily.WHISPER }.forEach { spec ->
                val lv = SherpaOfflineSpeechRecognitionProvider(dir, language = "lv", spec = spec)
                val config = lv.createModelConfig()
                assertEquals("lv", config.whisper.language)
                assertEquals("transcribe", config.whisper.task)
                assertEquals(spec.effectiveThreads(), config.numThreads)
                assertTrue(config.whisper.encoder.endsWith(spec.file("encoder")))
                lv.close()
                val auto = SherpaOfflineSpeechRecognitionProvider(dir, language = "auto", spec = spec)
                assertEquals("", auto.createModelConfig().whisper.language)
                auto.close()
            }
            val omni = SherpaOfflineSpeechRecognitionProvider(dir, spec = SherpaSpeechModelCatalog.omnilingual300m)
            assertTrue(omni.createModelConfig().omnilingual.model.endsWith("model.int8.onnx"))
            omni.close()
        } finally { dir.deleteRecursively() }
    }
    @Test fun verifiedSetRequiredAndFailedReplacementPreservesPreviousFile() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val bytes = "test-model-data".toByteArray()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val file = SherpaSpeechModelCatalog.FileSpec("model.onnx", bytes.size.toLong(), bytes.size.toLong(), sha, "model")
            val spec = SherpaSpeechModelCatalog.omnilingual300m.copy(files = listOf(file))
            File(dir, "model.onnx.partial").writeBytes(bytes)
            assertFalse(SherpaSpeechModelCatalog.isInstalled(dir, spec))
            File(dir, file.name).writeBytes(bytes)
            assertFalse(SherpaSpeechModelCatalog.isInstalled(dir, spec))
            assertTrue(SherpaModelInstaller.installFile(ByteArrayInputStream(bytes), dir, file) is SherpaModelInstaller.Result.Installed)
            assertTrue(SherpaSpeechModelCatalog.isInstalled(dir, spec))
            assertTrue(SherpaModelInstaller.installFile(ByteArrayInputStream(ByteArray(bytes.size)), dir, file) is SherpaModelInstaller.Result.Rejected)
            assertArrayEquals(bytes, File(dir, file.name).readBytes())
            assertFalse(File(dir, "model.onnx.partial").exists())
            File(dir, file.name).appendBytes(byteArrayOf(1))
            assertFalse(SherpaSpeechModelCatalog.isInstalled(dir, spec))
        } finally { dir.deleteRecursively() }
    }
}
