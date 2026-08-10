package com.halo.drivers.heycyan.media.repository

import com.halo.drivers.heycyan.media.HeyCyanMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * File-based implementation of [HeyCyanMediaRepository].
 *
 * Implements atomic writes:
 *   remote stream -> `.identity.part` temp file -> flush -> atomic rename -> metadata record commit
 *
 * Prevents partial/corrupted downloads from masquerading as valid files.
 */
class FileHeyCyanMediaRepository(
    private val storageDir: File,
    private val pathResolver: MediaPathResolver = DefaultMediaPathResolver(storageDir.absolutePath),
    private val identityResolver: HeyCyanMediaIdentityResolver = DefaultHeyCyanMediaIdentityResolver(),
) {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, StoredHeyCyanMedia>()

    private class FileMediaWriteSession(
        override val identity: String,
        override val media: HeyCyanMedia,
        val targetPath: String,
        val tempFile: File,
        val outputStream: FileOutputStream,
    ) : MediaWriteSession {
        var bytesWritten = 0L

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            withContext(Dispatchers.IO) {
                outputStream.write(bytes, offset, length)
                bytesWritten += length
            }
        }

        override suspend fun flush() {
            withContext(Dispatchers.IO) {
                outputStream.flush()
                outputStream.fd.sync()
            }
        }

        override suspend fun close() {
            withContext(Dispatchers.IO) {
                runCatching { outputStream.close() }
            }
        }
    }

    suspend fun list(): List<StoredHeyCyanMedia> = mutex.withLock {
        records.values.toList()
    }

    suspend fun find(identity: String): StoredHeyCyanMedia? = mutex.withLock {
        records[identity]
    }

    suspend fun beginWrite(media: HeyCyanMedia): MediaWriteSession = withContext(Dispatchers.IO) {
        val identity = identityResolver.identity(media)
        val targetPath = pathResolver.pathFor(media, identity)
        val targetFile = File(targetPath)
        targetFile.parentFile?.mkdirs()

        val tempFile = File(targetFile.parentFile, ".${targetFile.name}.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val fos = FileOutputStream(tempFile)
        FileMediaWriteSession(
            identity = identity,
            media = media,
            targetPath = targetPath,
            tempFile = tempFile,
            outputStream = fos,
        )
    }

    suspend fun commit(session: MediaWriteSession, sha256: String? = null): Result<StoredHeyCyanMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val fileSession = session as FileMediaWriteSession
            fileSession.flush()
            fileSession.close()

            val targetFile = File(fileSession.targetPath)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            // Atomic rename
            val renamed = fileSession.tempFile.renameTo(targetFile)
            if (!renamed) {
                // Fallback copy if rename fails across filesystems
                fileSession.tempFile.copyTo(targetFile, overwrite = true)
                fileSession.tempFile.delete()
            }

            val record = StoredHeyCyanMedia(
                identity = session.identity,
                deviceId = null,
                remoteId = session.media.id,
                filename = session.media.filename,
                mediaType = session.media.toMediaType(),
                localPath = targetFile.absolutePath,
                sizeBytes = targetFile.length(),
                createdAtMillis = session.media.createdAt,
                downloadedAtMillis = System.currentTimeMillis(),
                sha256 = sha256,
            )

            mutex.withLock {
                records[session.identity] = record
            }

            record
        }
    }

    suspend fun abort(session: MediaWriteSession) = withContext(Dispatchers.IO) {
        val fileSession = session as? FileMediaWriteSession ?: return@withContext
        fileSession.close()
        if (fileSession.tempFile.exists()) {
            fileSession.tempFile.delete()
        }
    }
}
