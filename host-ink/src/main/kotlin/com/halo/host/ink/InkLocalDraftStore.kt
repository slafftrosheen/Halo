package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Host-private local recovery file; runtime canonical checkpoints still go to Canvas. */
class InkLocalDraftStore(private val path: Path) {
    companion object {
        fun scopeKey(authenticatedHostSha256: String, boardId: String, documentId: String): String {
            require(authenticatedHostSha256.matches(Regex("[a-f0-9]{64}")))
            require(boardId.matches(Regex("[A-Za-z0-9_-]{1,200}")) && documentId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest("$authenticatedHostSha256/$boardId/$documentId".toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it) }
        }
    }
    fun load(): InkDocument? {
        if (!Files.exists(path)) return null
        require(Files.size(path) <= InkLimits.MAX_ENCODED_CHARS)
        val bytes = Files.newInputStream(path).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= InkLimits.MAX_ENCODED_CHARS)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return requireNotNull(InkCodec.decode(bytes.toString(Charsets.UTF_8))) { "Local drawing recovery unavailable" }
    }
    fun save(document: InkDocument) {
        val parent = requireNotNull(path.toAbsolutePath().parent)
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".halo-ink-", ".draft")
        try {
            val bytes = InkCodec.encode(document).toByteArray(Charsets.US_ASCII)
            java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
