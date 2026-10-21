package com.halo.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BrainArchiveCryptoTest {
    @Test fun portableBackupAuthenticatesPasswordAndRejectsCorruption() {
        val content = "Private notebook content".toByteArray()
        val buffer = ByteArrayOutputStream()
        BrainArchiveCrypto.encrypting(buffer, "my backup password".toCharArray()).use { it.write(content) }
        val encrypted = buffer.toByteArray()
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("Private notebook"))
        val restored = BrainArchiveCrypto.decrypting(ByteArrayInputStream(encrypted), "my backup password".toCharArray()).use { it.readBytes() }
        assertArrayEquals(content, restored)
        assertTrue(runCatching {
            BrainArchiveCrypto.decrypting(ByteArrayInputStream(encrypted), "wrong password".toCharArray()).use { it.readBytes() }
        }.isFailure)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertTrue(runCatching {
            BrainArchiveCrypto.decrypting(ByteArrayInputStream(encrypted), "my backup password".toCharArray()).use { it.readBytes() }
        }.isFailure)
    }
}
