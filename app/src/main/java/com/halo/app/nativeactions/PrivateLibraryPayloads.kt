package com.halo.app.nativeactions

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.payload.FileContentPayloadStore
import com.halo.app.data.payload.PayloadBodyEntity
import com.halo.app.data.payload.PayloadReferenceEntity
import com.halo.runtime.cognition.context.PayloadOwner
import com.halo.runtime.cognition.context.PayloadOwnerKind
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivateLibraryPayloadBody(
    val body: PayloadBodyEntity,
    val references: List<PayloadReferenceEntity>,
)

internal object PrivateLibraryPayloadCodec {
    const val maxReferences = 4096
    val digestPattern = Regex("[a-f0-9]{64}")

    fun encode(entry: PrivateLibraryPayloadBody): JSONObject = with(entry.body) {
        JSONObject().put("sha", sha256).put("bytes", byteCount).put("at", createdAtMs)
            .put("references", JSONArray(entry.references.map { row ->
                JSONObject().put("id", row.id).put("kind", row.ownerKind).put("owner", row.ownerId)
                    .put("type", row.mediaType).put("classification", row.classification).put("trust", row.trust)
                    .put("provenance", row.provenance).put("remote", row.remoteAllowed).put("at", row.createdAtMs)
            }))
    }

    fun decode(rows: JSONArray?): List<PrivateLibraryPayloadBody> {
        if (rows == null) return emptyList()
        require(rows.length() <= maxReferences)
        val entries = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val sha = row.getString("sha")
            val size = row.getLong("bytes")
            require(sha.matches(digestPattern) && size in 0..PrivateLibraryFilePayload.maxPlainBytes)
            val refs = row.getJSONArray("references")
            require(refs.length() in 1..maxReferences)
            PrivateLibraryPayloadBody(PayloadBodyEntity(sha, size, row.getLong("at")),
                (0 until refs.length()).map { refIndex -> with(refs.getJSONObject(refIndex)) {
                    val owner = getString("owner")
                    val id = getString("id")
                    require(owner.isNotBlank() && id.isNotBlank())
                    PayloadReferenceEntity(id, sha, PayloadOwnerKind.valueOf(getString("kind")).name, owner,
                        getString("type"), getString("classification"), getString("trust"), getString("provenance"),
                        getBoolean("remote"), getLong("at"))
                } })
        }
        check(entries.distinctBy { it.body.sha256 }.size == entries.size)
        val refs = entries.flatMap { it.references }
        check(refs.size <= maxReferences && refs.distinctBy { Triple(it.id, it.ownerKind, it.ownerId) }.size == refs.size)
        check(refs.groupBy { it.id }.values.all { group -> group.all { it.sha256 == group.first().sha256 } })
        return entries
    }
}

/** Tool-result bytes are immutable and deduplicated. The opaque reference and its owner govern access. */
internal class PrivateLibraryPayloads(
    private val database: AppDatabase,
    private val store: FileContentPayloadStore,
    private val directory: File,
) {
    private val dao get() = database.payloadDao()

    suspend fun <T> withAccess(action: suspend () -> T): T = store.withPrivateAccess(action)

    suspend fun sharedOwners(id: String): Set<PayloadOwner> = dao.sharedPrivateReferences(id)
        .mapTo(linkedSetOf()) { PayloadOwner(PayloadOwnerKind.valueOf(it.ownerKind), it.ownerId) }

    suspend fun capture(owners: Set<PayloadOwner>): List<PrivateLibraryPayloadBody> {
        val references = owners.map { it.id }.distinct().chunked(400).flatMap { dao.privateReferences(it) }
        require(references.size <= PrivateLibraryPayloadCodec.maxReferences)
        check(references.all { PayloadOwner(PayloadOwnerKind.valueOf(it.ownerKind), it.ownerId) in owners }) {
            "A tool-result reference has an unverified owner"
        }
        val allAliases = references.map { it.id }.distinct().chunked(400).flatMap { dao.referencesByIds(it) }
        check(allAliases.toSet() == references.toSet()) { "A tool-result link has an owner outside the reviewed group" }
        check(references.groupBy { it.id }.values.all { group -> group.all { it.sha256 == group.first().sha256 } })
        return references.groupBy { it.sha256 }.toSortedMap().map { (sha, rows) ->
            val body = requireNotNull(dao.body(sha))
            val file = store.body(sha)
            check(file.isFile && file.length() == body.byteCount) { "A stored tool result is missing" }
            PrivateLibraryPayloadBody(body.copy(unreferencedAtMs = null),
                rows.sortedWith(compareBy(PayloadReferenceEntity::id, PayloadReferenceEntity::ownerKind, PayloadReferenceEntity::ownerId)))
        }
    }

    /** Called with the public payload store's mutex held, inside the Private Room transaction. */
    suspend fun withdraw(access: VaultSession, envelopeId: String, entries: List<PrivateLibraryPayloadBody>) {
        for ((body, refs) in entries) {
            val sha = body.sha256
            val file = store.body(sha)
            val transfer = Transfer(envelopeId, sha)
            transfer.begin(Kind.LOCK)
            check(!transfer.sealed.exists())
            file.inputStream().buffered().use { input ->
                transfer.temporary.outputStream().use { output ->
                    PrivateLibraryFilePayload.seal(access, transfer.aad, body.byteCount, sha, input, output.buffered())
                    output.fd.sync()
                }
            }
            access.requireOpen()
            Files.move(transfer.temporary.toPath(), transfer.sealed.toPath(), ATOMIC_MOVE)
            for (ref in refs) check(dao.withdrawPrivateReference(ref.id, ref.ownerKind, ref.ownerId) == 1)
            if (dao.deleteUnreferenced(sha) == 1) {
                Files.move(file.toPath(), transfer.original.toPath(), ATOMIC_MOVE)
            }
        }
    }

    suspend fun restore(access: VaultSession, envelopeId: String, entries: List<PrivateLibraryPayloadBody>) {
        for ((body, refs) in entries) {
            val sha = body.sha256
            val file = store.body(sha)
            val transfer = Transfer(envelopeId, sha)
            val creating = !file.exists()
            transfer.begin(if (creating) Kind.RESTORE_BODY else Kind.RESTORE)
            check(transfer.sealed.isFile)
            if (creating) {
                check(dao.body(sha) == null) { "The public tool-result body is missing" }
                transfer.sealed.inputStream().buffered().use { input ->
                    transfer.temporary.outputStream().use { output ->
                        PrivateLibraryFilePayload.open(access, transfer.aad, body.byteCount, sha, input) { output.write(it) }
                        output.fd.sync()
                    }
                }
                access.requireOpen()
                val parent = checkNotNull(file.parentFile)
                check(parent.isDirectory || parent.mkdirs())
                Files.move(transfer.temporary.toPath(), file.toPath(), ATOMIC_MOVE)
            } else {
                check(file.isFile && file.length() == body.byteCount && digest(file) == sha) { "The shared tool result changed" }
            }
            dao.body(body)
            check(dao.body(sha)?.byteCount == body.byteCount)
            for (ref in refs) dao.reference(ref)
            dao.markReferenced(sha)
        }
    }

    fun preview(access: VaultSession, envelopeId: String, entry: PrivateLibraryPayloadBody): String? {
        val body = entry.body
        if (body.byteCount == 0L) return null
        val transfer = Transfer(envelopeId, body.sha256)
        val bytes = ByteArray(minOf(body.byteCount, 3_000L).toInt())
        try {
            PrivateLibraryFilePayload.randomAccess(access, transfer.aad, body.byteCount, transfer.sealed).use { source ->
                check(source.readAt(0, bytes, 0, bytes.size) == bytes.size)
                access.requireOpen()
                return bytes.toString(Charsets.UTF_8).trim().takeIf(String::isNotBlank)?.let {
                    if (body.byteCount > bytes.size) "$it…" else it
                }
            }
        } finally { bytes.fill(0) }
    }

    suspend fun recover() = withAccess { settle() }

    /** Replay using Room's commit decision. No authentication is needed to finish a move or roll it back. */
    suspend fun settle() {
        if (!directory.isDirectory) return
        database.withTransaction {
            val markers = checkNotNull(directory.listFiles()).mapNotNull { file ->
                markerName.matchEntire(file.name)?.let { parts ->
                    Triple(Transfer(parts.groupValues[1], parts.groupValues[2]), Kind.valueOf(parts.groupValues[3]), file)
                }
            }
            for ((transfer, kind, marker) in markers) {
                val publicFile = store.body(transfer.sha)
                val stayedPrivate = database.privateLibraryDao().summary(transfer.envelopeId) != null
                when (kind) {
                    Kind.LOCK -> if (stayedPrivate) {
                        check(transfer.sealed.isFile)
                        remove(transfer.original)
                    } else {
                        if (transfer.original.isFile) {
                            if (publicFile.exists()) {
                                check(digest(publicFile) == transfer.sha)
                                remove(transfer.original)
                            } else {
                                val parent = checkNotNull(publicFile.parentFile)
                                check(parent.isDirectory || parent.mkdirs())
                                Files.move(transfer.original.toPath(), publicFile.toPath(), ATOMIC_MOVE)
                            }
                        }
                        remove(transfer.sealed)
                    }
                    Kind.RESTORE, Kind.RESTORE_BODY -> if (stayedPrivate) {
                        if (kind == Kind.RESTORE_BODY && dao.body(transfer.sha) == null) remove(publicFile)
                    } else remove(transfer.sealed)
                }
                remove(transfer.temporary)
                remove(marker)
            }
        }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        try {
            file.inputStream().use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally { buffer.fill(0) }
    }

    private fun remove(file: File) { check(!file.exists() || file.delete()) }

    private enum class Kind { LOCK, RESTORE, RESTORE_BODY }

    private inner class Transfer(val envelopeId: String, val sha: String) {
        init { require(envelopeId.matches(envelopePattern) && sha.matches(PrivateLibraryPayloadCodec.digestPattern)) }
        private val prefix = "$envelopeId-$sha"
        val sealed = File(directory, "$prefix.sealed")
        val temporary = File(directory, "$prefix.pending")
        val original = File(directory, "$prefix.original")
        val aad = "private-library/tool-payload:v1/$envelopeId/$sha".toByteArray(Charsets.UTF_8)

        fun begin(kind: Kind) {
            check(directory.isDirectory || directory.mkdirs())
            check(!temporary.exists() && !original.exists())
            val marker = File(directory, "$prefix.${kind.name}")
            check(marker.createNewFile())
            marker.outputStream().use { it.fd.sync() }
        }
    }

    private companion object {
        const val envelope = "[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"
        val envelopePattern = Regex(envelope)
        val markerName = Regex("($envelope)-([a-f0-9]{64})\\.(LOCK|RESTORE|RESTORE_BODY)")
    }
}
