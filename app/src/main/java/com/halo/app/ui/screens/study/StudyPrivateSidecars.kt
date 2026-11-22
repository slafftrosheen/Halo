package com.halo.app.ui.screens.study

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.halo.sdk.runtime.Storage
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal data class StudyPrivateSidecars(val replies: String?, val journey: String?, val recovery: String?, val deleted: Boolean) {
    init {
        require((replies?.length ?: 0) <= 600_000 && (journey?.length ?: 0) <= 200_000 && (recovery?.length ?: 0) <= 200_000)
    }
    val hasContent: Boolean get() = replies != null || journey != null || recovery != null
    fun encode(): JSONObject = JSONObject().put("replies", replies).put("journey", journey).put("recovery", recovery)
        .put("deleted", deleted)

    companion object {
        fun decode(json: JSONObject) = StudyPrivateSidecars(
            json.optionalString("replies", 600_000), json.optionalString("journey", 200_000),
            json.optionalString("recovery", 200_000), json.getBoolean("deleted"),
        )

        private fun JSONObject.optionalString(name: String, max: Int): String? =
            if (isNull(name)) null else getString(name).also { require(it.length <= max) }
    }
}

internal enum class StudySidecarMove { WITHDRAW, RESTORE }

/** Recovers only after Room reveals whether the public session transaction committed. */
internal class StudySidecarJournal(private val storage: Storage) {
    fun pending(): List<String> {
        val array = JSONArray(storage.getString(INDEX) ?: "[]")
        require(array.length() <= 1000)
        return (0 until array.length()).map { array.getString(it).also(::requireId) }.also {
            require(it.toSet().size == it.size)
        }
    }

    fun stage(id: String, move: StudySidecarMove, snapshot: StudyPrivateSidecars, clear: Map<String, String?>,
        flags: Map<String, Boolean>) {
        requireId(id)
        check(id !in pending() && storage.getString(entry(id)) == null)
        val value = if (!snapshot.hasContent) {
            // This carries only a pre-existing deletion bit; there are no preference bytes to recover.
            if (snapshot.deleted) "empty:1" else "empty:0"
        } else {
            val keyAlias = alias(id)
            deleteKey(id)
            val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
                generateKey()
            }
            val encrypted = encrypt(key, id, move, snapshot)
            try { Base64.encodeToString(encrypted, Base64.NO_WRAP) } finally { encrypted.fill(0) }
        }
        val encoded = "${move.name}:$value"
        val index = JSONArray(pending() + id).toString()
        storage.putBatch(clear + mapOf(INDEX to index, entry(id) to encoded), flags)
        check(storage.getString(entry(id)) == encoded && storage.getString(INDEX) == index)
    }

    fun move(id: String): StudySidecarMove? = storage.getString(entry(id))?.let {
        StudySidecarMove.valueOf(it.substringBefore(':'))
    }

    fun read(id: String, move: StudySidecarMove): StudyPrivateSidecars {
        val entry = requireNotNull(storage.getString(entry(id))).removePrefix("${move.name}:")
        if (entry == "empty:0" || entry == "empty:1") return StudyPrivateSidecars(null, null, null, entry.endsWith('1'))
        require(entry.length <= 10_000_000)
        val bytes = Base64.decode(entry, Base64.NO_WRAP)
        val plain = try {
            require(bytes.size > IV_BYTES + 16)
            val key = keys().getKey(alias(id), null) as? SecretKey ?: error("Study recovery key unavailable")
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, IV_BYTES))
                updateAAD(aad(id, move))
                doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            }
        } finally { bytes.fill(0) }
        return try { StudyPrivateSidecars.decode(JSONObject(plain.toString(Charsets.UTF_8))) }
        finally { plain.fill(0) }
    }

    fun finish(id: String, strings: Map<String, String?>, flags: Map<String, Boolean>, remainsPrivate: Boolean) {
        val hasKey = requireNotNull(storage.getString(entry(id))).substringAfter(':') !in setOf("empty:0", "empty:1")
        if (hasKey && remainsPrivate) deleteKey(id)
        val remaining = pending().filterNot { it == id }
        val index = if (remaining.isEmpty()) null else JSONArray(remaining).toString()
        storage.putBatch(strings + mapOf(INDEX to index, entry(id) to null), flags)
        check(storage.getString(entry(id)) == null && storage.getString(INDEX) == index &&
            strings.all { (key, value) -> storage.getString(key) == value } &&
            flags.all { (key, value) -> storage.getBoolean(key, !value) == value })
        if (hasKey && !remainsPrivate) deleteKey(id)
    }

    private fun deleteKey(id: String) {
        val keyStore = keys()
        val alias = alias(id)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun encrypt(key: SecretKey, id: String, move: StudySidecarMove, snapshot: StudyPrivateSidecars): ByteArray {
        val plain = snapshot.encode().toString().toByteArray(Charsets.UTF_8)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key)
                check(iv.size == IV_BYTES)
                updateAAD(aad(id, move))
                iv + doFinal(plain)
            }
        } finally { plain.fill(0) }
    }

    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun entry(id: String) = "study_private_move_v1_$id"
    private fun aad(id: String, move: StudySidecarMove) = "halo-study-sidecar-v1:${move.name}:$id".toByteArray(Charsets.UTF_8)
    private fun alias(id: String) = "halo-study-sidecar-v1-" + MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun requireId(id: String) { require(id.matches(Regex("[A-Za-z0-9_.:-]{1,200}"))) }

    private companion object { const val INDEX = "study_private_move_index_v1"; const val IV_BYTES = 12 }
}
