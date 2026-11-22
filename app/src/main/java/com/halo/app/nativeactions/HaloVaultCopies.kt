package com.halo.app.nativeactions

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.OsConstants
import android.util.AtomicFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class VaultCopyLifetime(val createdAt: Long, val elapsedAt: Long, val boot: Int, val days: Int) {
    init { require(days in 1..7) }

    fun remaining(wall: Long, elapsed: Long, currentBoot: Int): Long {
        val allowed = TimeUnit.DAYS.toMillis(days.toLong())
        if (wall < createdAt) return 0
        val wallAge = wall - createdAt
        val sameBoot = currentBoot >= 0 && currentBoot == boot
        val elapsedAge = if (sameBoot) elapsed - elapsedAt else 0L
        if (elapsedAge < 0) return 0
        return (allowed - maxOf(wallAge, elapsedAge)).coerceAtLeast(0L)
    }
}

internal enum class VaultCopyFormat(val mime: String, val extension: String) {
    PHOTO("image/jpeg", "jpg"),
    PAPER_DETAILS("text/plain", "txt"),
}

internal object HaloVaultCopies {
    private const val keyAlias = "halo-vault-share-copies-v1"
    private const val capacity = 30
    private const val maximumBytes = 5 * 1024 * 1024
    private const val headerBytes = 13
    private val lock = Any()

    data class Copy(val displayName: String, val format: VaultCopyFormat, val lifetime: VaultCopyLifetime, val bytes: ByteArray) {
        fun clear() = bytes.fill(0)
    }

    fun stage(context: Context, paperName: String, format: VaultCopyFormat, bytes: ByteArray, days: Int, authorized: () -> Unit): Uri {
        require(bytes.size in 1..maximumBytes)
        val lifetime = VaultCopyLifetime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context), days)
        val id = UUID.randomUUID().toString()
        val displayName = paperName.map { if (it.isISOControl() || it == '/' || it == '\\') '_' else it }
            .joinToString("").trim().take(100).ifBlank { "Halo" } + "." + format.extension
        val target = file(context, id)
        synchronized(lock) {
            authorized()
            val root = requireNotNull(target.baseFile.parentFile)
            check(root.isDirectory || root.mkdirs())
            val now = System.currentTimeMillis()
            val oldest = now - TimeUnit.DAYS.toMillis(7)
            root.listFiles().orEmpty().filter { it.isFile && it.lastModified() in 1..oldest }.forEach { stale ->
                if (runCatching { UUID.fromString(stale.name).toString() == stale.name }.getOrDefault(false)) revoke(context, stale.name)
            }
            check(root.listFiles().orEmpty().count { it.isFile } < capacity)
            write(target, id, Copy(displayName, format, lifetime, bytes))
        }
        try {
            val request = OneTimeWorkRequestBuilder<HaloVaultCopyExpiryWorker>()
                .setInputData(workDataOf("copyId" to id))
                .setInitialDelay(days.toLong(), TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("halo-vault-copy-$id", ExistingWorkPolicy.KEEP, request).result.get()
            authorized()
        } catch (failure: Exception) {
            revoke(context, id)
            throw failure
        }
        return uri(context, id)
    }

    fun open(context: Context, id: String): Copy = synchronized(lock) {
        val result = read(file(context, id), id)
        if (remaining(context, result.lifetime) == 0L) {
            result.clear()
            revoke(context, id)
            throw FileNotFoundException("Vault copy expired")
        }
        result
    }

    fun isAvailable(context: Context, id: String, lifetime: VaultCopyLifetime): Boolean =
        remaining(context, lifetime) > 0 && file(context, id).baseFile.isFile

    fun expire(context: Context, id: String): Boolean = synchronized(lock) {
        val path = file(context, id)
        if (!path.baseFile.exists()) return@synchronized true
        val copy = read(path, id)
        try {
            if (remaining(context, copy.lifetime) > 0) false else {
                revoke(context, id)
                true
            }
        } finally { copy.clear() }
    }

    fun revoke(context: Context, id: String) = synchronized(lock) {
        file(context, id).delete()
        context.revokeUriPermission(uri(context, id), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun token(context: Context, uri: Uri): String {
        require(uri.scheme == "content" && uri.authority == "${context.packageName}.vault-copies" &&
            uri.pathSegments.size == 2 && uri.pathSegments[0] == "copy" && uri.query == null && uri.fragment == null)
        val id = uri.pathSegments[1]
        require(UUID.fromString(id).toString() == id)
        return id
    }

    private fun uri(context: Context, id: String): Uri = Uri.Builder().scheme("content")
        .authority("${context.packageName}.vault-copies").appendPath("copy").appendPath(id).build()

    private fun file(context: Context, id: String): AtomicFile {
        require(UUID.fromString(id).toString() == id)
        return AtomicFile(File(context.noBackupFilesDir, "halo-vault-copies-v1/$id"))
    }

    private fun bootCount(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun remaining(context: Context, lifetime: VaultCopyLifetime): Long =
        lifetime.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context))

    private fun key(create: Boolean): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keys.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }

    private fun write(target: AtomicFile, id: String, copy: Copy) {
        val encrypted = WipingBuffer(copy.bytes.size + 512).use { encoded ->
            val out = DataOutputStream(encoded)
            out.writeUTF(copy.displayName)
            out.writeUTF(copy.format.name)
            out.writeLong(copy.lifetime.createdAt)
            out.writeLong(copy.lifetime.elapsedAt)
            out.writeInt(copy.lifetime.boot)
            out.writeInt(copy.lifetime.days)
            out.writeInt(copy.bytes.size)
            out.write(copy.bytes)
            out.flush()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            check(cipher.iv.size == 12)
            cipher.updateAAD(id.toByteArray())
            byteArrayOf(1) + cipher.iv + encoded.finish(cipher)
        }
        val stream = target.startWrite()
        try {
            stream.write(encrypted)
            target.finishWrite(stream)
        } catch (error: Exception) { target.failWrite(stream); throw error }
    }

    private class WipingBuffer(size: Int) : ByteArrayOutputStream(size) {
        fun finish(cipher: Cipher): ByteArray = cipher.doFinal(buf, 0, count)
        override fun close() { buf.fill(0); reset() }
    }

    private fun read(path: AtomicFile, id: String): Copy {
        val encrypted = path.openRead().use { stream ->
            check(stream.channel.size() in (headerBytes + 16L)..(maximumBytes + 2048L))
            stream.readBytes()
        }
        require(encrypted[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, encrypted, 1, 12))
        cipher.updateAAD(id.toByteArray())
        val plain = cipher.doFinal(encrypted, headerBytes, encrypted.size - headerBytes)
        return try {
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                val name = input.readUTF()
                val format = VaultCopyFormat.valueOf(input.readUTF())
                val lifetime = VaultCopyLifetime(input.readLong(), input.readLong(), input.readInt(), input.readInt())
                val count = input.readInt()
                require(count in 1..maximumBytes && count == input.available())
                val bytes = ByteArray(count)
                input.readFully(bytes)
                Copy(name, format, lifetime, bytes)
            }
        } finally { plain.fill(0) }
    }
}

class HaloVaultCopyExpiryWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val id = inputData.getString("copyId") ?: return Result.failure()
        return try {
            if (HaloVaultCopies.expire(applicationContext, id)) Result.success() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
}

/** Only the exact URI granted by the Android chooser can read a reviewed, temporary copy. */
class HaloVaultCopyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = runCatching { read(uri) { it.format.mime } }.getOrNull()

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? = runCatching {
        read(uri) { copy ->
            val columns = projection?.map { it }?.toTypedArray() ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            val row = columns.map { column -> when (column) {
                OpenableColumns.DISPLAY_NAME -> copy.displayName
                OpenableColumns.SIZE -> copy.bytes.size.toLong()
                else -> null
            } }.toTypedArray()
            MatrixCursor(columns, 1).apply { addRow(row) }
        }
    }.getOrNull()

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || !readers.tryAcquire()) throw FileNotFoundException("Vault copy unavailable")
        var opened: HaloVaultCopies.Copy? = null
        var reader: ShareReader? = null
        try {
            val host = requireNotNull(context)
            val id = HaloVaultCopies.token(host, uri)
            val copy = HaloVaultCopies.open(host, id)
            opened = copy
            val storage = requireNotNull(host.getSystemService(StorageManager::class.java))
            val callback = ShareReader(host, id, copy)
            reader = callback
            return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback, handler)
        } catch (_: Exception) {
            val callback = reader
            if (callback != null) callback.onRelease() else { opened?.clear(); readers.release() }
            throw FileNotFoundException("Vault copy unavailable")
        }
    }

    private class ShareReader(private val host: Context, private val id: String, private val copy: HaloVaultCopies.Copy) :
        ProxyFileDescriptorCallback() {
        private val released = AtomicBoolean(false)
        private val wipe = Runnable { copy.clear() }

        init { handler.postDelayed(wipe, HaloVaultCopies.remaining(host, copy.lifetime)) }

        private fun checkAvailable() {
            if (released.get() || !HaloVaultCopies.isAvailable(host, id, copy.lifetime)) {
                copy.clear()
                throw ErrnoException("vaultCopy", OsConstants.ENOENT)
            }
        }

        override fun onGetSize(): Long { checkAvailable(); return copy.bytes.size.toLong() }

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            checkAvailable()
            if (offset < 0 || size < 0) throw ErrnoException("vaultCopy", OsConstants.EINVAL)
            if (offset >= copy.bytes.size) return 0
            val start = offset.toInt()
            val count = minOf(data.size, size, copy.bytes.size - start)
            copy.bytes.copyInto(data, endIndex = start + count, startIndex = start)
            return count
        }

        override fun onRelease() {
            if (!released.compareAndSet(false, true)) return
            handler.removeCallbacks(wipe)
            copy.clear()
            readers.release()
        }
    }

    private inline fun <T> read(uri: Uri, block: (HaloVaultCopies.Copy) -> T): T {
        val host = requireNotNull(context)
        val copy = HaloVaultCopies.open(host, HaloVaultCopies.token(host, uri))
        return try { block(copy) } finally { copy.clear() }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        val readers = Semaphore(8)
        val handler by lazy { Handler(HandlerThread("HaloVaultCopies").apply { start() }.looper) }
    }
}
