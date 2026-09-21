package com.halo.app.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BrainArchiveCrypto {
    val MAGIC = "HALOBRAIN".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1
    const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    fun encrypting(output: OutputStream, password: CharArray): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val data = DataOutputStream(output)
        data.write(MAGIC)
        data.writeInt(VERSION)
        data.writeInt(ITERATIONS)
        data.writeInt(salt.size)
        data.write(salt)
        data.writeInt(iv.size)
        data.write(iv)
        data.flush()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveArchiveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(headerBytes(VERSION, ITERATIONS, salt, iv))
        return CipherOutputStream(output, cipher)
    }

    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        require(magic.contentEquals(MAGIC)) { "Not an encrypted Halo Brain archive" }
        require(data.readInt() == VERSION) { "Unsupported encrypted archive version" }
        val iterations = data.readInt()
        require(iterations in 100_000..2_000_000) { "Invalid archive KDF work factor" }
        val saltLength = data.readInt()
        require(saltLength in 16..64)
        val salt = ByteArray(saltLength).also(data::readFully)
        val ivLength = data.readInt()
        require(ivLength in 12..32)
        val iv = ByteArray(ivLength).also(data::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveArchiveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(headerBytes(VERSION, iterations, salt, iv))
        return CipherInputStream(input, cipher)
    }

    fun deriveArchiveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun headerBytes(version: Int, iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC)
                output.writeInt(version)
                output.writeInt(iterations)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(iv.size)
                output.write(iv)
            }
        }.toByteArray()
}
