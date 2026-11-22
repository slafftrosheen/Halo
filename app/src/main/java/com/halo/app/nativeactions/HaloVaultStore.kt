package com.halo.app.nativeactions

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

internal enum class VaultPaperKind { WARRANTY, RECEIPT, POLICY, DOCUMENT, PRIVATE_NOTE }

internal data class VaultPaper(
    val name: String = "",
    val kind: VaultPaperKind = VaultPaperKind.DOCUMENT,
    val bought: LocalDate? = null,
    val ends: LocalDate? = null,
    val remind: LocalDate? = null,
    val id: String = UUID.randomUUID().toString(),
    val revision: Long = 0,
    val photo: Boolean = false,
    val folder: String = "",
    val text: String = "",
) {
    fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(name.isNotBlank() && name.length <= 180 && revision >= 0)
        require(folder.length <= 80 && folder.none(Char::isISOControl))
        require(bought == null || ends == null || bought <= ends)
        require(remind == null || ends == null || remind <= ends)
        require(text.length <= 2400 && text.none { it.isISOControl() && it != '\n' && it != '\t' })
        require(if (kind == VaultPaperKind.PRIVATE_NOTE) text.isNotBlank() && !photo && bought == null && ends == null && remind == null
            else text.isEmpty())
    }
}

internal class VaultSession(private val bytes: ByteArray, private val requireAuthority: () -> Unit) {
    private val active = AtomicBoolean(true)

    fun requireOpen() {
        check(active.get()) { "Vault is locked" }
        requireAuthority()
    }

    fun <T> withKey(block: (SecretKey) -> T): T {
        val copy = synchronized(bytes) { requireOpen(); bytes.copyOf() }
        return try { block(SecretKeySpec(copy, "AES")) } finally { copy.fill(0) }
    }

    fun close() {
        active.set(false)
        synchronized(bytes) { bytes.fill(0) }
    }
}

internal class VaultUnlock internal constructor(
    val cipher: Cipher,
    internal val ciphertext: ByteArray?,
) {
    private val unused = AtomicBoolean(true)
    internal fun claim() { check(unused.compareAndSet(true, false)) }
}

/** Every document and photo lives under noBackupFilesDir, separate from the assistant and Workspace. */
internal class HaloVaultStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "halo-vault-v1")
    private val keyFile = AtomicFile(File(root, "key.bin"))
    private val index = AtomicFile(File(root, "papers.bin"))

    /** Android 11+ associates this exact Keystore Cipher with the strong device prompt. */
    fun prepareUnlock(): VaultUnlock = synchronized(lock) {
        val encrypted = read(keyFile, maxKey)
        val wrappingKey = wrappingKey(create = encrypted == null)
        if (encrypted == null) {
            check(!hasVaultData())
            VaultUnlock(encryptor(wrappingKey, null), null)
        } else {
            VaultUnlock(decryptor(wrappingKey, null, encrypted),
                encrypted.copyOfRange(headerBytes, encrypted.size))
        }
    }

    fun finishUnlock(request: VaultUnlock, requireAuthority: () -> Unit): VaultSession = synchronized(lock) {
        requireAuthority()
        request.claim()
        request.cipher.updateAAD(keyContext)
        val ciphertext = request.ciphertext
        if (ciphertext != null) return@synchronized VaultSession(request.cipher.doFinal(ciphertext).also {
            require(it.size == secretBytes)
        }, requireAuthority)
        check(read(keyFile, maxKey) == null && !hasVaultData())
        val secret = ByteArray(secretBytes).apply(random::nextBytes)
        try {
            write(keyFile, seal(request.cipher, secret))
            VaultSession(secret, requireAuthority)
        } catch (error: Exception) {
            secret.fill(0)
            throw error
        }
    }

    fun list(session: VaultSession): List<VaultPaper> = synchronized(lock) {
        load(session).also { rows ->
            val kept = rows.filter(VaultPaper::photo).mapTo(hashSetOf()) { it.id + ".image" }
            // Clean unreferenced encrypted photos left when an interrupted write committed no index.
            root.listFiles()?.filter { it.name.endsWith(".image") && it.name !in kept }
                ?.forEach { AtomicFile(it).delete() }
        }
    }

    fun save(session: VaultSession, draft: VaultPaper, photo: ByteArray?): VaultPaper = synchronized(lock) {
        draft.validate()
        val existing = load(session)
        val current = existing.firstOrNull { it.id == draft.id }
        check((current?.revision ?: 0) == draft.revision && (current != null || existing.size < maxPapers))
        require(photo == null || (current == null && photo.size in 1..maxPhotoBytes))
        require(draft.kind != VaultPaperKind.PRIVATE_NOTE || photo == null && current?.photo != true)
        val updated = draft.copy(revision = draft.revision + 1, photo = current?.photo == true || photo != null)
        val attachment = attachment(updated.id)
        if (photo != null) session.withKey { key -> write(attachment, seal(encryptor(key, photoContext(updated.id)), photo)) }
        try {
            session.requireOpen()
            saveIndex(session, existing.filterNot { it.id == draft.id } + updated)
        } catch (error: Exception) {
            if (photo != null) attachment.delete()
            throw error
        }
        updated
    }

    fun viewPhoto(session: VaultSession, paper: VaultPaper): ByteArray = synchronized(lock) {
        check(load(session).any { it.id == paper.id && it.revision == paper.revision && it.photo })
        val bytes = requireNotNull(read(attachment(paper.id), maxPhotoBytes + headerBytes + 16))
        session.withKey { key -> open(key, photoContext(paper.id), bytes) }
    }

    fun current(session: VaultSession, paper: VaultPaper): VaultPaper = synchronized(lock) {
        requireNotNull(load(session).firstOrNull { it.id == paper.id && it.revision == paper.revision })
    }

    fun delete(session: VaultSession, paper: VaultPaper) = synchronized(lock) {
        val rows = load(session)
        check(rows.any { it.id == paper.id && it.revision == paper.revision })
        saveIndex(session, rows.filterNot { it.id == paper.id })
        attachment(paper.id).delete()
    }

    private fun load(session: VaultSession): List<VaultPaper> {
        session.requireOpen()
        val encrypted = read(index, maxIndex) ?: return emptyList()
        val plain = session.withKey { key -> open(key, indexContext, encrypted) }
        return try {
            val items = JSONArray(plain.toString(Charsets.UTF_8))
            require(items.length() <= maxPapers)
            List(items.length()) { position ->
                val row = items.getJSONObject(position)
                fun date(key: String) = row.optString(key).takeIf(String::isNotBlank)?.let(LocalDate::parse)
                VaultPaper(row.getString("name"), VaultPaperKind.valueOf(row.getString("kind")),
                    date("bought"), date("ends"), date("remind"), row.getString("id"), row.getLong("revision"),
                    row.getBoolean("photo"), row.optString("folder"), row.optString("text")).also { it.validate(); require(it.revision > 0) }
            }.also { rows -> require(rows.distinctBy(VaultPaper::id).size == rows.size) }
        } finally { plain.fill(0) }
    }

    private fun saveIndex(session: VaultSession, rows: List<VaultPaper>) {
        val plain = JSONArray(rows.map { row ->
            JSONObject().put("name", row.name).put("kind", row.kind.name).put("bought", row.bought?.toString())
                .put("ends", row.ends?.toString()).put("remind", row.remind?.toString()).put("id", row.id)
                .put("revision", row.revision).put("photo", row.photo).put("folder", row.folder)
                .put("text", row.text.takeIf(String::isNotEmpty))
        }).toString().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= maxIndex - headerBytes - 16)
            val encrypted = session.withKey { key -> seal(encryptor(key, indexContext), plain) }
            session.requireOpen()
            write(index, encrypted)
        } finally { plain.fill(0) }
    }

    private fun hasVaultData() = root.listFiles()?.any { it.name.startsWith("papers.bin") || it.name.contains(".image") } == true
    private fun attachment(id: String) = AtomicFile(File(root, UUID.fromString(id).toString() + ".image"))
    private fun photoContext(id: String) = ("halo-vault-photo-v1:" + id).toByteArray(Charsets.UTF_8)

    private fun wrappingKey(create: Boolean): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create && !hasVaultData())
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(0,
            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(20)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(builder.build()) }.generateKey()
    }

    private fun encryptor(key: SecretKey, purpose: ByteArray?) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, key)
        require(iv.size == ivBytes)
        if (purpose != null) updateAAD(purpose)
    }

    private fun decryptor(key: SecretKey, purpose: ByteArray?, envelope: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        require(envelope.size >= headerBytes + 16 && envelope[0] == version)
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope, 1, ivBytes))
        if (purpose != null) updateAAD(purpose)
    }

    private fun open(key: SecretKey, purpose: ByteArray, data: ByteArray) =
        decryptor(key, purpose, data).doFinal(data, headerBytes, data.size - headerBytes)

    private fun seal(cipher: Cipher, plain: ByteArray): ByteArray =
        byteArrayOf(version) + cipher.iv + cipher.doFinal(plain)

    private fun read(file: AtomicFile, maximum: Int): ByteArray? = try {
        file.openRead().use { stream ->
            check(stream.channel.size() in 1L..maximum.toLong())
            stream.readBytes()
        }
    } catch (missing: java.io.FileNotFoundException) {
        if (file.baseFile.exists()) throw missing else null
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        check(root.isDirectory || root.mkdirs())
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Exception) { file.failWrite(stream); throw failure }
        check(file.readFully().contentEquals(bytes)) { "Vault write did not finish" }
    }

    private companion object {
        val lock = Any()
        val random = SecureRandom()
        const val alias = "halo-vault-wrapping-v1"
        const val secretBytes = 32
        const val ivBytes = 12
        const val headerBytes = 1 + ivBytes
        const val version: Byte = 1
        const val maxPapers = 500
        const val maxKey = 256
        const val maxIndex = 1024 * 1024
        const val maxPhotoBytes = 5 * 1024 * 1024
        val keyContext = "halo-vault-key-v1".toByteArray(Charsets.UTF_8)
        val indexContext = "halo-vault-index-v1".toByteArray(Charsets.UTF_8)
    }
}
