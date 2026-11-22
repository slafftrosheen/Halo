package com.halo.app.nativeactions

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal interface PrivateLibraryReadSource : Closeable {
    val size: Long
    /** Returns -1 at EOF. Only authenticated chunks are released. */
    fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int
}

/** Fixed-size authenticated chunks allow a large recording to move without loading it into Room or memory. */
internal object PrivateLibraryFilePayload {
    val maxPlainBytes: Long get() = chunkBytes.toLong() * Int.MAX_VALUE

    suspend fun seal(access: VaultSession, aad: ByteArray, size: Long, digest: String, input: InputStream, output: OutputStream) {
        require(size in 0..maxPlainBytes)
        val scope = currentCoroutineContext()
        val source = DataInputStream(input)
        val sealed = DataOutputStream(output)
        val plain = ByteArray(chunkBytes)
        val prefix = ByteArray(8).also(SecureRandom()::nextBytes)
        val hash = MessageDigest.getInstance("SHA-256")
        try {
            withFileKey(access, aad) { key ->
                sealed.writeInt(magic)
                sealed.write(prefix)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                var left = size
                var index = 0
                do {
                    scope.ensureActive()
                    access.requireOpen()
                    val count = minOf(left, chunkBytes.toLong()).toInt()
                    source.readFully(plain, 0, count)
                    hash.update(plain, 0, count)
                    cipher.init(Cipher.ENCRYPT_MODE, key, parameters(prefix, index++))
                    cipher.updateAAD(aad)
                    sealed.write(cipher.doFinal(plain, 0, count))
                    left -= count
                } while (left > 0)
                check(source.read() == -1 && hash.hexDigest() == digest)
                sealed.flush()
            }
        } finally {
            plain.fill(0)
        }
    }

    suspend fun open(access: VaultSession, aad: ByteArray, size: Long, digest: String,
        input: InputStream, receive: (ByteArray) -> Unit,
    ) {
        require(size in 0..maxPlainBytes)
        val scope = currentCoroutineContext()
        val source = DataInputStream(input)
        val sealed = ByteArray(chunkBytes + tagBytes)
        val prefix = ByteArray(8)
        val hash = MessageDigest.getInstance("SHA-256")
        try {
            check(source.readInt() == magic)
            source.readFully(prefix)
            withFileKey(access, aad) { key ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                var left = size
                var index = 0
                do {
                    scope.ensureActive()
                    access.requireOpen()
                    val count = minOf(left, chunkBytes.toLong()).toInt()
                    source.readFully(sealed, 0, count + tagBytes)
                    cipher.init(Cipher.DECRYPT_MODE, key, parameters(prefix, index++))
                    cipher.updateAAD(aad)
                    val plain = cipher.doFinal(sealed, 0, count + tagBytes)
                    try {
                        check(plain.size == count)
                        hash.update(plain)
                        receive(plain)
                    } finally {
                        plain.fill(0)
                    }
                    left -= count
                } while (left > 0)
                check(source.read() == -1 && hash.hexDigest() == digest)
            }
        } finally {
            sealed.fill(0)
        }
    }

    fun randomAccess(access: VaultSession, aad: ByteArray, size: Long, file: File): PrivateLibraryReadSource {
        access.requireOpen()
        val source = RandomAccessFile(file, "r")
        return try {
            check(size in 0..maxPlainBytes)
            val chunks = (size / chunkBytes + if (size % chunkBytes != 0L) 1 else 0).coerceAtLeast(1)
            check(source.length() == headerBytes + size + chunks * tagBytes)
            check(source.readInt() == magic)
            val prefix = ByteArray(headerBytes - Int.SIZE_BYTES).also(source::readFully)
            ChunkReader(access, aad, size, source, prefix)
        } catch (error: Throwable) {
            source.close()
            throw error
        }
    }

    fun inMemory(access: VaultSession, bytes: ByteArray): PrivateLibraryReadSource = object : PrivateLibraryReadSource {
        override val size = bytes.size.toLong()
        private var closed = false

        @Synchronized
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            check(!closed)
            access.requireOpen()
            require(position >= 0 && offset >= 0 && length in 0..target.size - offset)
            if (length == 0) return 0
            if (position >= size) return -1
            val count = minOf(length.toLong(), size - position).toInt()
            bytes.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }

        @Synchronized
        override fun close() {
            closed = true
            bytes.fill(0)
        }
    }

    private class ChunkReader(
        private val access: VaultSession,
        private val aad: ByteArray,
        override val size: Long,
        private val file: RandomAccessFile,
        private val prefix: ByteArray,
    ) : PrivateLibraryReadSource {
        private var closed = false
        private var previousIndex = -1
        private var previousChunk = ByteArray(0)

        @Synchronized
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            check(!closed)
            access.requireOpen()
            require(position >= 0 && offset >= 0 && length in 0..target.size - offset)
            if (length == 0) return 0
            if (position >= size) return -1
            val count = minOf(length.toLong(), size - position).toInt()
            var copied = 0
            while (copied < count) {
                val cursor = position + copied
                val index = (cursor / chunkBytes).toInt()
                val chunk = chunk(index)
                access.requireOpen()
                val start = (cursor % chunkBytes).toInt()
                val available = minOf(count - copied, chunk.size - start)
                chunk.copyInto(target, offset + copied, start, start + available)
                copied += available
            }
            return count
        }

        private fun chunk(index: Int): ByteArray {
            if (previousIndex == index) return previousChunk
            previousChunk.fill(0)
            previousChunk = ByteArray(0)
            previousIndex = -1
            val length = minOf(chunkBytes.toLong(), size - index.toLong() * chunkBytes).toInt()
            val sealed = ByteArray(length + tagBytes)
            file.seek(headerBytes + index.toLong() * (chunkBytes + tagBytes))
            file.readFully(sealed)
            try {
                withFileKey(access, aad) { key ->
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, parameters(prefix, index))
                    cipher.updateAAD(aad)
                    val plain = cipher.doFinal(sealed)
                    check(plain.size == length)
                    previousChunk = plain
                    previousIndex = index
                }
            } finally {
                sealed.fill(0)
            }
            return previousChunk
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            previousChunk.fill(0)
            file.close()
        }
    }

    private fun parameters(prefix: ByteArray, index: Int) = GCMParameterSpec(128,
        ByteBuffer.allocate(12).put(prefix).putInt(index).array())

    private fun withFileKey(access: VaultSession, context: ByteArray, use: (SecretKey) -> Unit) {
        access.withKey { master ->
            val raw = master.encoded
            val derived = try {
                Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(raw, "HmacSHA256")) }.doFinal(context)
            } finally {
                raw.fill(0)
            }
            try { use(SecretKeySpec(derived, "AES")) } finally { derived.fill(0) }
        }
    }

    private fun MessageDigest.hexDigest() = digest().joinToString("") { "%02x".format(it) }

    private const val chunkBytes = 256 * 1024
    private const val tagBytes = 16
    private const val magic = 0x484c4631
    private const val headerBytes = 12
}
