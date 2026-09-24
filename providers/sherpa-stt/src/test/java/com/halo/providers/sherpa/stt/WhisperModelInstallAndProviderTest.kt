package com.halo.providers.sherpa.stt

import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperModelInstallAndProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val spec = SherpaSpeechModelCatalog.whisperTiny

    @Test
    fun testMissingModel() = runBlocking {
        val dir = tempFolder.newFolder("missing_model")
        assertFalse(SherpaSpeechModelCatalog.isInstalled(dir, spec))
        val missing = SherpaSpeechModelCatalog.missingFiles(dir, spec)
        assertEquals(3, missing.size)

        val provider = ReloadableSpeechRecognitionProvider(dir)
        assertFalse(provider.availability.value)
        assertFalse(provider.health())
    }

    @Test
    fun testPartialModel() = runBlocking {
        val dir = tempFolder.newFolder("partial_model")
        // Create 1 small file (< minimumBytes)
        val file1 = File(dir, "tiny-encoder.int8.onnx")
        file1.writeBytes(ByteArray(500))

        assertFalse(SherpaSpeechModelCatalog.isInstalled(dir, spec))
        val provider = ReloadableSpeechRecognitionProvider(dir)
        assertFalse(provider.health())
    }

    @Test
    fun testCompleteModelValidation() {
        val dir = tempFolder.newFolder("complete_model")
        // Create dummy files that meet minimum byte requirements
        spec.files.forEach { fileSpec ->
            val f = File(dir, fileSpec.name)
            f.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var written = 0L
                while (written < fileSpec.minimumBytes) {
                    out.write(buffer)
                    written += buffer.size
                }
            }
        }

        assertTrue(SherpaSpeechModelCatalog.isInstalled(dir, spec))
        assertTrue(SherpaSpeechModelCatalog.missingFiles(dir, spec).isEmpty())
    }

    @Test
    fun testInstallFailure_rejectedFilename() {
        val dir = tempFolder.newFolder("install_rejected")
        val invalidSpec = SherpaSpeechModelCatalog.FileSpec("../invalid.onnx", 100L)
        val input = ByteArrayInputStream(ByteArray(500))

        val result = SherpaModelInstaller.installFile(input, dir, invalidSpec)
        assertTrue(result is SherpaModelInstaller.Result.Rejected)
    }

    @Test
    fun testSuccessfulInstallFile() {
        val dir = tempFolder.newFolder("install_success")
        val fileSpec = SherpaSpeechModelCatalog.FileSpec("tiny-tokens.txt", 1000L)
        val input = ByteArrayInputStream(ByteArray(2000))

        val result = SherpaModelInstaller.installFile(input, dir, fileSpec)
        assertTrue(result is SherpaModelInstaller.Result.Installed)
        val installedFile = (result as SherpaModelInstaller.Result.Installed).file
        assertTrue(installedFile.exists())
        assertEquals("tiny-tokens.txt", installedFile.name)
        assertTrue(installedFile.length() >= 1000L)
    }

    @Test
    fun testProviderBecomingAvailableAfterInstall() = runBlocking {
        val dir = tempFolder.newFolder("dynamic_provider")
        val provider = ReloadableSpeechRecognitionProvider(dir)

        // Initially missing model -> health is false
        assertFalse(provider.health())

        // Install all 3 files
        spec.files.forEach { fileSpec ->
            val input = ByteArrayInputStream(ByteArray((fileSpec.minimumBytes + 1000).toInt()))
            val result = SherpaModelInstaller.installFile(input, dir, fileSpec)
            assertTrue(result is SherpaModelInstaller.Result.Installed)
        }

        assertTrue(SherpaSpeechModelCatalog.isInstalled(dir, spec))

        // Refresh provider
        provider.refresh().join()

        // Provider availability/health update without recreating the wrapper.
        assertTrue(provider.availability.value)
        assertTrue(provider.health())
    }
}
