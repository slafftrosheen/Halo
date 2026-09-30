package com.halo.app.data.payload

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.runtime.cognition.context.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** App-private immutable bodies; Room records ownership, trust and provenance per opaque reference. */
class FileContentPayloadStore(
    private val db: AppDatabase, private val directory: File,
    private val limits: PayloadLimits = PayloadLimits(), private val now: () -> Long = System::currentTimeMillis,
) : ContentPayloadStore {
    private val mutex = Mutex()
    private val dao get() = db.payloadDao()
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun body(sha: String): File {
        require(sha.matches(Regex("[0-9a-f]{64}")))
        return File(File(directory, sha.take(2)), sha)
    }
    private suspend fun domain(row: PayloadReferenceEntity): PayloadRef? {
        val stored = dao.bodies().firstOrNull { it.sha256 == row.sha256 } ?: return null
        val classification = PayloadClassification.entries.firstOrNull { it.name == row.classification } ?: return null
        val trust = ContextTrust.entries.firstOrNull { it.name == row.trust } ?: return null
        return PayloadRef(row.id, row.sha256, row.mediaType, stored.byteCount, row.createdAtMs,
            classification, trust, row.provenance, row.remoteAllowed)
    }
    private suspend fun <T> safely(failure: LongTaskFailure, action: suspend () -> PayloadOutcome<T>): PayloadOutcome<T> = try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { PayloadOutcome.Failed(failure) }
        catch (_: android.database.SQLException) { PayloadOutcome.Failed(failure) }
        catch (_: IllegalStateException) { PayloadOutcome.Failed(failure) }
        catch (_: IllegalArgumentException) { PayloadOutcome.Failed(failure) }

    override suspend fun put(owner: PayloadOwner, bytes: ByteArray, classification: PayloadClassification,
        trust: ContextTrust, provenance: String, remoteAllowed: Boolean, mediaType: String): PayloadOutcome<PayloadRef> = safely(LongTaskFailure.PAYLOAD_STORE_FULL) { withContext(Dispatchers.IO) {
        mutex.withLock {
            if (owner.id.isBlank() || provenance.length > 1024 || mediaType.length > 128)
                return@withLock PayloadOutcome.Failed(LongTaskFailure.SOURCE_NOT_ALLOWED)
            if (bytes.size.toLong() > limits.singleBytes) return@withLock PayloadOutcome.Failed(LongTaskFailure.EVIDENCE_TOO_LARGE)
            val sha = digest(bytes)
            val refs = dao.references()
            val bodies = dao.bodies()
            refs.firstOrNull { it.sha256 == sha && it.ownerKind == owner.kind.name && it.ownerId == owner.id &&
                it.classification == classification.name && it.trust == trust.name && it.provenance == provenance &&
                it.remoteAllowed == remoteAllowed && it.mediaType == mediaType }?.let {
                val existing = body(sha)
                if (existing.isFile && existing.length() == bytes.size.toLong() && digest(existing.readBytes()) == sha)
                    domain(it)?.let { ref -> return@withLock PayloadOutcome.Value(ref) }
                return@withLock PayloadOutcome.Failed(LongTaskFailure.PAYLOAD_MISSING)
            }
            val owned = refs.filter { it.ownerKind == owner.kind.name && it.ownerId == owner.id }.map { it.sha256 }.toSet()
            val ownerBytes = bodies.filter { it.sha256 in owned }.sumOf { it.byteCount }
            val physicalBytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val file = body(sha)
            val additional = if (file.exists()) 0L else bytes.size.toLong()
            if (ownerBytes + (if (sha in owned) 0 else bytes.size) > limits.perOwnerBytes ||
                physicalBytes + additional > limits.totalBytes || refs.size >= 4096)
                return@withLock PayloadOutcome.Failed(LongTaskFailure.PAYLOAD_STORE_FULL)
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            if (file.exists() && (file.length() != bytes.size.toLong() || digest(file.readBytes()) != sha))
                return@withLock PayloadOutcome.Failed(LongTaskFailure.PAYLOAD_MISSING)
            if (!file.exists()) {
                val temporary = File(file.parentFile, ".${UUID.randomUUID()}.tmp")
                try {
                    FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
                    check(digest(temporary.readBytes()) == sha)
                    check(temporary.renameTo(file))
                } finally { temporary.delete() }
            }
            val row = PayloadReferenceEntity(UUID.randomUUID().toString(), sha, owner.kind.name, owner.id,
                mediaType, classification.name, trust.name, provenance, remoteAllowed, now())
            db.withTransaction {
                dao.body(PayloadBodyEntity(sha, bytes.size.toLong(), now()))
                dao.reference(row)
                dao.markReferenced(sha)
            }
            android.util.Log.i("HaloPayload", "HALO_PAYLOAD_STORED id=${row.id.take(8)} classification=$classification bytes=${bytes.size} deduplicated=${additional == 0L}")
            snapshotStats()
            PayloadOutcome.Value(PayloadRef(row.id, sha, mediaType, bytes.size.toLong(), row.createdAtMs, classification, trust, provenance, remoteAllowed))
        }
    }
    }
    override suspend fun reference(owner: PayloadOwner, refId: String): PayloadRef? = mutex.withLock {
        dao.reference(refId, owner.kind.name, owner.id)?.let { domain(it) }
    }
    override suspend fun read(owner: PayloadOwner, refId: String, offset: Long, maxBytes: Int, targetIsLocal: Boolean): PayloadOutcome<ByteArray> = safely(LongTaskFailure.PAYLOAD_MISSING) { withContext(Dispatchers.IO) {
        mutex.withLock {
            val row = dao.reference(refId, owner.kind.name, owner.id)
                ?: return@withLock PayloadOutcome.Failed(LongTaskFailure.SOURCE_NOT_ALLOWED)
            if (!targetIsLocal && !row.remoteAllowed) return@withLock PayloadOutcome.Failed(LongTaskFailure.SOURCE_NOT_ALLOWED)
            val file = body(row.sha256)
            if (!file.isFile || file.length() > limits.singleBytes) return@withLock PayloadOutcome.Failed(LongTaskFailure.PAYLOAD_MISSING)
            if (offset < 0 || offset > file.length()) return@withLock PayloadOutcome.Failed(LongTaskFailure.SOURCE_NOT_ALLOWED)
            val bytes = file.readBytes()
            if (digest(bytes) != row.sha256) return@withLock PayloadOutcome.Failed(LongTaskFailure.PAYLOAD_MISSING)
            val start = offset.toInt()
            val bounded = bytes.copyOfRange(start, (start + maxBytes.coerceIn(1, limits.readBytes)).coerceAtMost(bytes.size))
            android.util.Log.i("HaloPayload", "HALO_PAYLOAD_READ id=${refId.take(8)} bytes=${bounded.size} offset=$offset")
            PayloadOutcome.Value(bounded)
        }
    }
    }
    override suspend fun retain(source: PayloadOwner, refId: String, destination: PayloadOwner): Boolean = mutex.withLock {
        val row = dao.reference(refId, source.kind.name, source.id) ?: return@withLock false
        if (source == destination) return@withLock true
        if (dao.reference(refId, destination.kind.name, destination.id) != null) return@withLock true
        val refs = dao.references()
        val owned = refs.filter { it.ownerKind == destination.kind.name && it.ownerId == destination.id }.map { it.sha256 }.toSet()
        val bodies = dao.bodies()
        val used = bodies.filter { it.sha256 in owned }.sumOf { it.byteCount }
        val additional = if (row.sha256 in owned) 0L else bodies.firstOrNull { it.sha256 == row.sha256 }?.byteCount ?: return@withLock false
        if (destination.id.isBlank() || used + additional > limits.perOwnerBytes || refs.size >= 4096) return@withLock false
        db.withTransaction {
            dao.reference(row.copy(ownerKind = destination.kind.name, ownerId = destination.id))
            dao.markReferenced(row.sha256)
        }
        true
    }
    override suspend fun release(owner: PayloadOwner) = mutex.withLock {
        db.withTransaction { dao.release(owner.kind.name, owner.id); dao.markUnreferenced(now()) }
        snapshotStats()
        Unit
    }
    override suspend fun stats(): PayloadStoreStats = mutex.withLock { snapshotStats() }
    private suspend fun snapshotStats(): PayloadStoreStats {
        val bodies = dao.bodies(); val live = dao.references().map { it.sha256 }.toSet()
        val unused = bodies.filter { it.sha256 !in live }
        val total = bodies.sumOf { it.byteCount }; val unreferenced = unused.sumOf { it.byteCount }
        return PayloadStoreStats(bodies.size, total, total - unreferenced, unreferenced, unused.mapNotNull { it.unreferencedAtMs }.minOrNull()).also {
            com.halo.runtime.cognition.delegation.LongTaskDiagnostics.payloads.value = it
        }
    }
    override suspend fun collectUnreferenced(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.withTransaction { dao.releaseMissingOwners(); dao.markUnreferenced(now()) }
            val refs = dao.references().map { it.sha256 }.toSet()
            val bodies = dao.bodies()
            var removed = 0
            for (stored in bodies) {
                if (stored.sha256 !in refs && stored.unreferencedAtMs != null && now() - stored.unreferencedAtMs > limits.graceMs && dao.deleteUnreferenced(stored.sha256) == 1) {
                    if (body(stored.sha256).delete()) removed++
                }
            }
            val known = dao.bodies().map { it.sha256 }.toSet()
            directory.walkTopDown().filter { it.isFile && it.name !in known && now() - it.lastModified() > limits.graceMs }.forEach {
                if (it.delete()) removed++
            }
            snapshotStats()
            android.util.Log.i("HaloPayload", "HALO_PAYLOAD_GC removed=$removed")
            removed
        }
    }
}
