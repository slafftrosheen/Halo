package com.halo.drivers.heycyan.media.repository

import com.halo.drivers.heycyan.media.HeyCyanMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

interface HeyCyanMediaRepository {
    suspend fun list(): List<StoredHeyCyanMedia>
    suspend fun find(identity: String): StoredHeyCyanMedia?
    suspend fun beginWrite(media: HeyCyanMedia): MediaWriteSession
    suspend fun commit(session: MediaWriteSession, sha256: String? = null): Result<StoredHeyCyanMedia>
    suspend fun abort(session: MediaWriteSession)
}

/** Durable cache. Immutable blob names plus an atomically replaced index mean failed
 * replacements retain the previous valid file, including after process death.
 * Unindexed/partial bytes are never accepted as completed downloads. */
class FileHeyCyanMediaRepository(
    private val storageDir: File,
    private val pathResolver: MediaPathResolver = DefaultMediaPathResolver(storageDir.absolutePath),
    private val identityResolver: HeyCyanMediaIdentityResolver = DefaultHeyCyanMediaIdentityResolver(),
    private val maxFileBytes: Long = 256L * 1024 * 1024,
) : HeyCyanMediaRepository {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, StoredHeyCyanMedia>()
    private var loaded = false
    private val root get() = storageDir.canonicalFile
    private val index get() = File(root, "media-index-v1.bin")
    private class Session(override val identity: String, override val media: HeyCyanMedia,
        val temporary: File, val output: FileOutputStream, val owner: FileHeyCyanMediaRepository) : MediaWriteSession {
        var bytesWritten = 0L
        var closed = false
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            check(!closed && length >= 0 && bytesWritten <= owner.maxFileBytes - length) { "Device media size limit exceeded" }
            output.write(bytes, offset, length); bytesWritten += length
        }
        override suspend fun flush() = withContext(Dispatchers.IO) { check(!closed); output.flush(); output.fd.sync() }
        override suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
            if (!closed) { closed = true; output.close() }
        }
    }
    private fun inside(file: File): Boolean = file.canonicalPath.startsWith(root.path + File.separator)
    private fun valid(record: StoredHeyCyanMedia): Boolean = File(record.localPath).let {
        inside(it) && it.isFile && it.length() == record.sizeBytes && record.sizeBytes in 1..maxFileBytes
    }
    private fun load() {
        if (loaded) return
        check(root.exists() || root.mkdirs())
        if (index.exists()) {
            try {
                DataInputStream(BufferedInputStream(index.inputStream())).use { input ->
                    require(input.readInt() == 0x484D4931)
                    val count = input.readInt(); require(count in 0..5000)
                    repeat(count) {
                        val id = input.readUTF(); val remote = input.readUTF(); val name = input.readUTF()
                        val type = HeyCyanMediaType.valueOf(input.readUTF()); val relative = input.readUTF()
                        val size = input.readLong(); val created = input.readLong(); val downloaded = input.readLong(); val hash = input.readUTF()
                        require(id.length in 1..512 && name.length in 1..255 && hash.matches(Regex("[a-f0-9]{64}")))
                        val file = File(root, relative)
                        val record = StoredHeyCyanMedia(id, null, remote.ifEmpty { null }, name, type, file.absolutePath,
                            size, created.takeIf { it >= 0 }, downloaded, hash)
                        if (valid(record)) records[id] = record
                    }
                    require(input.read() == -1)
                }
            } catch (_: Exception) {
                // Corrupt index cannot authorize a skip/import. Preserve original bytes for diagnosis.
                records.clear()
            }
        }
        loaded = true
    }
    private fun persist(next: Map<String, StoredHeyCyanMedia>) {
        require(next.size <= 5000)
        val temp = File(root, "index-${UUID.randomUUID()}.part")
        try {
            FileOutputStream(temp).use { stream ->
                val out = DataOutputStream(BufferedOutputStream(stream))
                out.writeInt(0x484D4931); out.writeInt(next.size)
                next.values.forEach { r ->
                    require(inside(File(r.localPath)))
                    out.writeUTF(r.identity); out.writeUTF(r.remoteId.orEmpty()); out.writeUTF(r.filename)
                    out.writeUTF(r.mediaType.name); out.writeUTF(File(r.localPath).relativeTo(root).path)
                    out.writeLong(r.sizeBytes); out.writeLong(r.createdAtMillis ?: -1); out.writeLong(r.downloadedAtMillis); out.writeUTF(requireNotNull(r.sha256))
                }
                out.flush(); stream.fd.sync()
            }
            Files.move(temp.toPath(), index.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    override suspend fun list(): List<StoredHeyCyanMedia> = withContext(Dispatchers.IO) { mutex.withLock {
        load(); records.values.filter(::valid)
    } }
    override suspend fun find(identity: String): StoredHeyCyanMedia? = withContext(Dispatchers.IO) { mutex.withLock {
        load(); records[identity]?.takeIf(::valid)
    } }
    override suspend fun beginWrite(media: HeyCyanMedia): MediaWriteSession = withContext(Dispatchers.IO) {
        val identity = identityResolver.identity(media)
        require(identity.length in 1..512 && media.filename.length in 1..255)
        require(media.sizeBytes == null || media.sizeBytes!! in 1..maxFileBytes)
        // Validate even a custom resolver; it cannot escape the selected device cache.
        require(inside(File(pathResolver.pathFor(media, identity))))
        val directory = File(root, "blobs").also { check(it.exists() || it.mkdirs()) }
        val temp = File(directory, "${UUID.randomUUID()}.part")
        Session(identity, media, temp, FileOutputStream(temp), this@FileHeyCyanMediaRepository)
    }
    override suspend fun commit(session: MediaWriteSession, sha256: String?): Result<StoredHeyCyanMedia> = withContext(Dispatchers.IO) {
        val s = session as? Session ?: return@withContext Result.failure(IllegalArgumentException("Foreign media write"))
        require(s.owner === this@FileHeyCyanMediaRepository)
        var published = false
        val destination = File(s.temporary.parentFile, s.temporary.name.removeSuffix(".part") + ".media")
        try {
            s.flush(); s.close(); currentCoroutineContext().ensureActive()
            check(s.bytesWritten in 1..maxFileBytes && s.temporary.length() == s.bytesWritten)
            check(s.media.sizeBytes == null || s.media.sizeBytes == s.bytesWritten) { "Device media length changed" }
            val digest = MessageDigest.getInstance("SHA-256")
            s.temporary.inputStream().use { input -> val bytes = ByteArray(64 * 1024); while (true) {
                currentCoroutineContext().ensureActive(); val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n)
            } }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(sha256 == null || sha256 == actual) { "Downloaded media digest mismatch" }
            val record = StoredHeyCyanMedia(s.identity, null, s.media.id, s.media.filename, s.media.toMediaType(),
                destination.absolutePath, s.bytesWritten, s.media.createdAt, System.currentTimeMillis(), actual)
            // Publication is short and indivisible with respect to cancellation.
            withContext(NonCancellable) { mutex.withLock {
                load()
                Files.move(s.temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                val old = records[s.identity]
                val next = records + (s.identity to record)
                persist(next); records[s.identity] = record; published = true
                old?.let { if (inside(File(it.localPath)) && it.localPath != record.localPath) File(it.localPath).delete() }
            } }
            Result.success(record)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
        finally { withContext(NonCancellable) { s.close(); s.temporary.delete(); if (!published) destination.delete() } }
    }
    override suspend fun abort(session: MediaWriteSession) = withContext(NonCancellable + Dispatchers.IO) {
        val s = session as? Session ?: return@withContext
        require(s.owner === this@FileHeyCyanMediaRepository); s.close(); s.temporary.delete(); Unit
    }
}
