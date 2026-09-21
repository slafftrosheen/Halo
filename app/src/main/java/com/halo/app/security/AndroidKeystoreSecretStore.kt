package com.halo.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.halo.app.storage.HaloStorageLayout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreSecretStore(
    private val layout: HaloStorageLayout,
) : SecretStore {
    override suspend fun put(key: String, value: String?) = withContext(Dispatchers.IO) {
        putBlocking(key, value)
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        getBlocking(key)
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        AtomicFile(secretFile(key)).delete()
        Unit
    }

    fun putBlocking(key: String, value: String?) {
        if (value == null) {
            AtomicFile(secretFile(key)).delete()
            return
        }
        layout.secretFiles.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val target = secretFile(key)
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.writeInt(ciphertext.size)
            output.write(ciphertext)
            output.flush()
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    fun getBlocking(key: String): String? {
        val source = secretFile(key)
        if (!source.isFile) return null
        return runCatching {
            DataInputStream(AtomicFile(source).openRead()).use { input ->
                require(input.readInt() == FORMAT_VERSION)
                val ivLength = input.readInt()
                require(ivLength in 12..32)
                val iv = ByteArray(ivLength).also(input::readFully)
                val encryptedLength = input.readInt()
                require(encryptedLength in 1..MAX_SECRET_BYTES)
                val encrypted = ByteArray(encryptedLength).also(input::readFully)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun secretFile(key: String): File {
        require(key.matches(Regex("[a-zA-Z0-9._-]{1,80}"))) { "Invalid secret key" }
        return File(layout.secretFiles, "$key.bin")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "halo.local.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val MAX_SECRET_BYTES = 1024 * 1024
    }
}
