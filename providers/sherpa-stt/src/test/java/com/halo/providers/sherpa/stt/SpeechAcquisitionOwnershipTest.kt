package com.halo.providers.sherpa.stt

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SpeechAcquisitionOwnershipTest {
    private val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
    private val other = "c88fe002-14e0-48dc-85bd-6dbcd038f709"
    private val bytes = "model-fixture".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val file = SherpaSpeechModelCatalog.FileSpec("model.onnx", 1, bytes.size.toLong(), hash)
    private val spec = SherpaSpeechModelCatalog.ModelSpec("fixture", listOf(file), "")

    private fun fixture(block: (File) -> Unit) {
        val directory = java.nio.file.Files.createTempDirectory("halo-owned-speech-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
    private fun install(directory: File, acquisitionOwner: String? = owner, requestedOwner: String? = acquisitionOwner) =
        SherpaModelInstaller.installFile(ByteArrayInputStream(bytes), directory, file, acquisitionOwner, requestedOwner)

    @Test fun `rollback removes only its verified artifact and keeps unrelated files`() = fixture { directory ->
        assertTrue(install(directory) is SherpaModelInstaller.Result.Installed)
        val unrelated = File(directory, "user-model.onnx").apply { writeText("keep") }
        assertFalse(SherpaModelInstaller.removeOwnedFiles(directory, spec, other))
        assertTrue(File(directory, file.name).exists())
        assertTrue(SherpaModelInstaller.removeOwnedFiles(directory, spec, owner))
        assertFalse(File(directory, file.name).exists())
        assertEquals("keep", unrelated.readText())
    }

    @Test fun `explicit independent import clears previous setup claim`() = fixture { directory ->
        assertTrue(install(directory) is SherpaModelInstaller.Result.Installed)
        assertTrue(install(directory, null) is SherpaModelInstaller.Result.Installed)
        assertFalse(SherpaModelInstaller.removeOwnedFiles(directory, spec, owner))
        assertArrayEquals(bytes, File(directory, file.name).readBytes())
    }

    @Test fun `changed bytes are retained even when size and receipt still match`() = fixture { directory ->
        install(directory)
        val changed = bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        File(directory, file.name).writeBytes(changed)
        assertThrows(IllegalStateException::class.java) { SherpaModelInstaller.removeOwnedFiles(directory, spec, owner) }
        assertArrayEquals(changed, File(directory, file.name).readBytes())
    }

    @Test fun `setup cannot replace independent final while completing borrowed partial`() = fixture { directory ->
        install(directory, null)
        assertTrue(install(directory, null, owner) is SherpaModelInstaller.Result.Rejected)
        assertArrayEquals(bytes, File(directory, file.name).readBytes())
        assertFalse(SherpaModelInstaller.hasOwnedFile(directory, file, owner))
    }

    @Test fun `borrowed prefix publication stays independent`() = fixture { directory ->
        assertTrue(install(directory, null, owner) is SherpaModelInstaller.Result.Installed)
        assertFalse(SherpaModelInstaller.removeOwnedFiles(directory, spec, owner))
        assertTrue(File(directory, file.name).exists())
    }

    @Test fun `partial cleanup requires exact transaction receipt`() = fixture { directory ->
        val partial = File(directory, "${file.name}.$hash.download").apply { writeText("prefix") }
        val receipt = File(directory, partial.name + ".acquired").apply { writeText("$owner:$hash:${bytes.size}") }
        assertFalse(SherpaModelInstaller.removeOwnedFiles(directory, spec, other))
        assertTrue(partial.exists())
        assertTrue(SherpaModelInstaller.removeOwnedFiles(directory, spec, owner))
        assertFalse(partial.exists())
        assertFalse(receipt.exists())
    }
}
