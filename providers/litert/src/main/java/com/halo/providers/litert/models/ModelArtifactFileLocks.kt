package com.halo.providers.litert.models

import java.io.Closeable
import java.io.RandomAccessFile
import java.io.File
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock

/** Cross-process, per-model file pins shared by the existing manager and the benchmark bridge. */
internal object ModelArtifactFileLocks {
    private data class Entry(
        val local: ReentrantReadWriteLock = ReentrantReadWriteLock(),
        var readers: Int = 0,
        var readFile: RandomAccessFile? = null,
        var readLock: FileLock? = null,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun acquireRead(root: File, modelId: String): Closeable? {
        val key = key(root, modelId)
        val entry = entries.computeIfAbsent(key) { Entry() }
        val local = entry.local.readLock()
        local.lock()
        synchronized(entry) {
            if (entry.readers == 0) {
                val file = openLockFile(root, modelId) ?: run { local.unlock(); return null }
                val lock = runCatching { file.channel.tryLock(0L, Long.MAX_VALUE, true) }.getOrNull()
                if (lock == null) {
                    file.close()
                    local.unlock()
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
            local.unlock()
        }
    }

    fun tryAcquireWrite(root: File, modelId: String): Closeable? {
        val key = key(root, modelId)
        val entry = entries.computeIfAbsent(key) { Entry() }
        val local = entry.local.writeLock()
        if (!local.tryLock()) return null
        val file = openLockFile(root, modelId)
        if (file == null) {
            local.unlock()
            return null
        }
        val lock = runCatching { file.channel.tryLock() }.getOrNull()
        if (lock == null) {
            file.close()
            local.unlock()
            return null
        }
        val closed = AtomicBoolean(false)
        return Closeable {
            if (!closed.compareAndSet(false, true)) return@Closeable
            runCatching { lock.release() }
            runCatching { file.close() }
            local.unlock()
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
