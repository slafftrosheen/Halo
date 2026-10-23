package com.halo.providers.litert.models

import java.io.Closeable
import java.io.RandomAccessFile
import java.io.File
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Cross-process, per-model file pins shared by the existing manager and the benchmark bridge. */
internal object ModelArtifactFileLocks {
    private data class Entry(
        var writer: Boolean = false,
        var readers: Int = 0,
        var readFile: RandomAccessFile? = null,
        var readLock: FileLock? = null,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun acquireRead(root: File, modelId: String): Closeable? {
        val key = key(root, modelId)
        val entry = entries.computeIfAbsent(key) { Entry() }
        synchronized(entry) {
            if (entry.writer) return null
            if (entry.readers == 0) {
                val file = openLockFile(root, modelId) ?: return null
                val lock = runCatching { file.channel.tryLock(0L, Long.MAX_VALUE, true) }.getOrNull()
                if (lock == null) {
                    file.close()
                    return null
                }
                entry.readFile = file
                entry.readLock = lock
            }
            entry.readers++
        }
        val closed = AtomicBoolean(false)
        return Closeable {
            if (!closed.compareAndSet(false, true)) return@Closeable
            synchronized(entry) {
                entry.readers--
                if (entry.readers == 0) {
                    runCatching { entry.readLock?.release() }
                    runCatching { entry.readFile?.close() }
                    entry.readLock = null
                    entry.readFile = null
                }
            }
        }
    }

    fun tryAcquireWrite(root: File, modelId: String): Closeable? {
        val key = key(root, modelId)
        val entry = entries.computeIfAbsent(key) { Entry() }
        synchronized(entry) {
            if (entry.writer || entry.readers > 0) return null
            val file = openLockFile(root, modelId) ?: return null
            val lock = runCatching { file.channel.tryLock() }.getOrNull()
            if (lock == null) {
                file.close()
                return null
            }
            entry.writer = true
            val closed = AtomicBoolean(false)
            // A lease belongs to an operation, not a thread. Suspended owners may close
            // on a different dispatcher thread; FileLock itself permits that.
            return Closeable {
                if (!closed.compareAndSet(false, true)) return@Closeable
                synchronized(entry) {
                    try {
                        runCatching { lock.release() }
                        runCatching { file.close() }
                    } finally { entry.writer = false }
                }
            }
        }
    }

    private fun openLockFile(root: File, modelId: String): RandomAccessFile? = runCatching {
        val directory = File(root, ".lab-leases")
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
        RandomAccessFile(File(directory, "${digest(modelId)}.lock"), "rw")
    }.getOrNull()

    private fun key(root: File, modelId: String): String =
        "${runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)}:${digest(modelId)}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
