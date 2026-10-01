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
        putBlocking(key, null)
    }

    override suspend fun migrateIfAbsent(key: String, legacyValue: String): String = withContext(Dispatchers.IO) {
        synchronized(storeLock) {
            getBlocking(key)?.takeIf { it.isNotBlank() } ?: legacyValue.also { putBlocking(key, it) }
        }
    }

    fun putBlocking(key: String, value: String?) = synchronized(storeLock) {
        if (value == null) {
            AtomicFile(secretFile(key)).delete()
            return@synchronized
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

    fun getBlocking(key: String): String? = synchronized(storeLock) {
        val source = secretFile(key)
        if (!source.isFile && !File(source.path + ".bak").isFile) return@synchronized null

        // An absent file means "not configured". Any failure after a file exists means
        // "configured data is unreadable" and must propagate to the owner. Silently
        // converting corruption, a missing Keystore alias, or GCM authentication failure
        // into null made provider setup look as if the user had never saved a key.
        DataInputStream(AtomicFile(source).openRead()).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported protected secret format" }
            val ivLength = input.readInt()
            require(ivLength in 12..32) { "Invalid protected secret IV" }
            val iv = ByteArray(ivLength).also(input::readFully)
            val encryptedLength = input.readInt()
            require(encryptedLength in 1..MAX_SECRET_BYTES) { "Invalid protected secret length" }
            val encrypted = ByteArray(encryptedLength).also(input::readFully)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val encryptionKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: error("Stored credential encryption key is unavailable")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }
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
        // Shared by bootstrap migration and the runtime facade; neither may replace
        // the alias or race AtomicFile operations while the other writes a secret.
        val storeLock = Any()
        const val KEY_ALIAS = "halo.local.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val MAX_SECRET_BYTES = 1024 * 1024
    }
}
