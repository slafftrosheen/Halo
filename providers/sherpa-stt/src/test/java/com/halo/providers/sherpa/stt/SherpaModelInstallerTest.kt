package com.halo.providers.sherpa.stt

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaModelInstallerTest {
    @Test
    fun `incomplete file is rejected and partial file is removed`() {
        val directory = createTempDir(prefix = "halo-installer-")
        try {
            val spec = SherpaSpeechModelCatalog.FileSpec("model.bin", 8)
            val result = SherpaModelInstaller.installFile(
                input = ByteArrayInputStream(ByteArray(4)),
                destinationDirectory = directory,
                fileSpec = spec
            )

            assertTrue(result is SherpaModelInstaller.Result.Rejected)
            assertTrue(!File(directory, "model.bin").exists())
            assertTrue(!File(directory, ".model.bin.partial").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `complete file is installed atomically`() {
        val directory = createTempDir(prefix = "halo-installer-")
        try {
            val spec = SherpaSpeechModelCatalog.FileSpec("model.bin", 8)
            val result = SherpaModelInstaller.installFile(
                input = ByteArrayInputStream(ByteArray(16) { it.toByte() }),
                destinationDirectory = directory,
                fileSpec = spec
            )

            assertTrue(result is SherpaModelInstaller.Result.Installed)
            assertEquals(16L, File(directory, "model.bin").length())
            assertTrue(!File(directory, ".model.bin.partial").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
