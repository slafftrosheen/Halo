package com.halo.app.data.memory

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.runtime.cognition.memory.MemoryProposalOrigin
import com.halo.runtime.cognition.memory.MemoryEvidenceRef
import com.halo.runtime.cognition.memory.MemoryLimits
import com.halo.runtime.cognition.memory.MemorySensitivityClassifier
import com.halo.runtime.cognition.memory.MemoryProposalFingerprint
import com.halo.runtime.cognition.memory.MemoryConflictKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class MemoryRecord(val id: String, val content: String, val kind: MemoryKind, val scope: MemoryScope,
    val scopeId: String?, val createdAt: Long, val updatedAt: Long, val sourceObjectId: WorkspaceObjectId?,
    val sourceRevision: Int?, val confidence: Float?, val status: MemoryStatus,
    val origin: MemoryProposalOrigin = MemoryProposalOrigin.IMPORT, val evidence: List<MemoryEvidenceRef> = emptyList(),
    val sourceEpisodeId: String? = null, val evidenceCount: Int = 0, val lastConfirmedAt: Long? = null,
    val usageCount: Long = 0, val lastUsedAt: Long? = null, val validUntil: Long? = null, val userConfirmed: Boolean = false)
data class MemoryRevision(val id: String, val memoryId: String, val previousContent: String, val newContent: String, val changedAt: Long, val reason: String)
data class MemoryCandidate(val content: String, val sourceObjectId: WorkspaceObjectId? = null, val sourceRevision: Int? = null,
    val kind: MemoryKind = MemoryKind.FACT, val scope: MemoryScope = MemoryScope.GLOBAL, val scopeId: String? = null,
    val confidence: Float = 1f, val origin: MemoryProposalOrigin = MemoryProposalOrigin.USER_EXPLICIT,
    val evidence: List<MemoryEvidenceRef> = emptyList(), val sourceEpisodeId: String? = null,
    val validUntil: Long? = null, val acceptedByUser: Boolean = false)
data class MemoryConflict(val kind: MemoryConflictKind, val records: List<MemoryRecord>)
interface MemoryRepository {
    fun observeMemories(): Flow<List<MemoryRecord>>
    suspend fun getMemory(id: String): MemoryRecord?
    suspend fun promoteCandidate(candidate: MemoryCandidate): MemoryRecord
    suspend fun correctMemory(id: String, newContent: String, reason: String = "User correction"): Boolean
    suspend fun forgetMemory(id: String): Boolean
    fun observeRevisions(memoryId: String): Flow<List<MemoryRevision>>
    suspend fun conflict(candidate: MemoryCandidate): MemoryConflict = MemoryConflict(MemoryConflictKind.NEW, emptyList())
    suspend fun markUsed(ids: List<String>) = Unit
    suspend fun contextCandidates(scopes: Set<MemoryScope>, scopeIds: Set<String>): List<MemoryRecord> = emptyList()
}
class RoomMemoryRepository(private val db: AppDatabase, private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }) : MemoryRepository {
    override fun observeMemories() = db.memoryRecordDao().observeActiveRecords().map { rows -> rows.map { it.toDomain() } }
    override suspend fun getMemory(id: String) = db.memoryRecordDao().getRecord(id)?.toDomain()
    override suspend fun conflict(candidate: MemoryCandidate): MemoryConflict {
        val relevant = db.memoryRecordDao().relevant(candidate.scope.name, candidate.scopeId).filter { it.kind == candidate.kind.name }
        val exact = relevant.filter { MemoryProposalFingerprint.normalize(it.content) == MemoryProposalFingerprint.normalize(candidate.content) }
        if(exact.isNotEmpty()) return MemoryConflict(MemoryConflictKind.REINFORCES, exact.take(5).map { it.toDomain() })
        val subject = MemoryProposalFingerprint.subject(candidate.content)
        val different = relevant.filter { MemoryProposalFingerprint.subject(it.content) == subject }
        return MemoryConflict(if(different.isEmpty()) MemoryConflictKind.NEW else MemoryConflictKind.CONFLICTS, different.take(5).map { it.toDomain() })
    }
    override suspend fun promoteCandidate(candidate: MemoryCandidate): MemoryRecord = db.withTransaction {
        MemorySensitivityClassifier.requireWritable(candidate.content)
        require(candidate.confidence.isFinite() && candidate.confidence in 0f..1f)
        require(candidate.evidence.size <= MemoryLimits.EVIDENCE)
        require(candidate.scope == MemoryScope.GLOBAL || !candidate.scopeId.isNullOrBlank()) { "Choose the memory scope target" }
        require(candidate.scope != MemoryScope.CONVERSATION) { "Conversation working memory is transient" }
        require(candidate.kind != MemoryKind.PROJECT || candidate.scope == MemoryScope.PROJECT) { "Project conclusions require project scope" }
        require(candidate.kind != MemoryKind.EPISODE) { "Episodes use the episode repository" }
        require(candidate.origin in setOf(MemoryProposalOrigin.USER_EXPLICIT, MemoryProposalOrigin.USER_CORRECTION) || candidate.acceptedByUser) { "Inferred memories require proposal acceptance" }
        val confidence = if(candidate.origin in setOf(MemoryProposalOrigin.MODEL_INFERRED, MemoryProposalOrigin.EPISODE_DERIVED, MemoryProposalOrigin.WORKSPACE_DERIVED)) candidate.confidence.coerceAtMost(0.95f) else candidate.confidence
        val conflict = conflict(candidate)
        if(conflict.kind == MemoryConflictKind.REINFORCES) {
            val existing = conflict.records.first(); db.memoryRecordDao().reinforce(existing.id, now()); return@withTransaction requireNotNull(getMemory(existing.id))
        }
        require(conflict.kind != MemoryConflictKind.CONFLICTS) { "Conflicting memory requires explicit update review" }
        val timestamp = now(); val id = newId()
        val evidence=candidate.evidence.ifEmpty { candidate.sourceObjectId?.let {
            listOf(MemoryEvidenceRef(com.halo.runtime.cognition.memory.MemoryEvidenceKind.WORKSPACE_OBJECT,it.value,candidate.sourceRevision?.toString()))
        } ?: listOf(MemoryEvidenceRef(com.halo.runtime.cognition.memory.MemoryEvidenceKind.USER_ACTION,"approved:$id")) }
        db.memoryRecordDao().insertRecord(MemoryRecordEntity(id, candidate.content.trim(), candidate.kind.name, candidate.scope.name, candidate.scopeId,
            timestamp, timestamp, candidate.sourceObjectId?.value, candidate.sourceRevision, confidence, MemoryStatus.ACTIVE.name,
            candidate.origin.name, PersonalMemoryCodec.evidence(evidence), candidate.sourceEpisodeId, evidence.size, timestamp,
            validUntil = candidate.validUntil, fingerprint = MemoryProposalFingerprint.of(candidate.kind, candidate.scope, candidate.scopeId, candidate.content), userConfirmed = true))
        db.notebookDao().insertWorkspaceObject(WorkspaceObjectEntity(id, "MEMORY", candidate.content.trim().take(80), timestamp, timestamp,
            "USER_CREATED", "ACTIVE", false, "READY", "memory:$id"))
        android.util.Log.i("HALO_MEMORY_ACCEPTED", "memory=${id.take(8)} origin=${candidate.origin}")
        requireNotNull(getMemory(id))
    }
    override suspend fun correctMemory(id: String, newContent: String, reason: String): Boolean = db.withTransaction {
        MemorySensitivityClassifier.requireWritable(newContent)
        val current = db.memoryRecordDao().getRecord(id)?.takeIf { it.status == "ACTIVE" } ?: return@withTransaction false
        val text = newContent.trim(); val timestamp = now()
        if(current.content == text) { db.memoryRecordDao().reinforce(id, timestamp); return@withTransaction true }
        db.memoryRecordDao().insertRevision(MemoryRevisionEntity(newId(), id, current.content, text, timestamp, reason.take(240)))
        db.memoryRecordDao().insertRecord(current.copy(content = text, updatedAt = timestamp, origin = MemoryProposalOrigin.USER_CORRECTION.name,
            confidence = 1f, userConfirmed = true, lastConfirmedAt = timestamp,
            evidenceJson = PersonalMemoryCodec.evidence(listOf(MemoryEvidenceRef(com.halo.runtime.cognition.memory.MemoryEvidenceKind.USER_ACTION,"approved:${newId()}"))),
            evidenceCount = (current.evidenceCount + 1).coerceAtMost(1000000),
            fingerprint = MemoryProposalFingerprint.of(MemoryKind.valueOf(current.kind), MemoryScope.valueOf(current.scope), current.scopeId, text)))
        db.notebookDao().updateTitle(id, text.take(80), timestamp)
        supersedeProposals(current)
        db.memoryRecordDao().getRecord(id)?.let { supersedeProposals(it) }
        true
    }
    /** Accepted inference updates preserve its origin/confidence rather than impersonating a user statement. */
    suspend fun acceptUpdate(id: String, candidate: MemoryCandidate): Boolean = db.withTransaction {
        val old = db.memoryRecordDao().getRecord(id)?.takeIf { it.status == "ACTIVE" } ?: return@withTransaction false
        MemorySensitivityClassifier.requireWritable(candidate.content)
        require(candidate.acceptedByUser && old.kind == candidate.kind.name && old.scope == candidate.scope.name && old.scopeId == candidate.scopeId)
        val time = now(); val text = candidate.content.trim()
        if(old.content != text) db.memoryRecordDao().insertRevision(MemoryRevisionEntity(newId(), id, old.content, text, time, "User accepted updated memory"))
        db.memoryRecordDao().insertRecord(old.copy(content = text, updatedAt = time, confidence = candidate.confidence.coerceAtMost(0.95f), origin = candidate.origin.name,
            evidenceJson = PersonalMemoryCodec.evidence(candidate.evidence), sourceEpisodeId = candidate.sourceEpisodeId, evidenceCount = old.evidenceCount + candidate.evidence.size,
            lastConfirmedAt = time, userConfirmed = true, fingerprint = MemoryProposalFingerprint.of(candidate.kind,candidate.scope,candidate.scopeId,text)))
        db.notebookDao().updateTitle(id,text.take(80),time); true
    }
    override suspend fun forgetMemory(id: String): Boolean = db.withTransaction {
        val forgotten = db.memoryRecordDao().forget(id,now()) > 0
        if(forgotten) {
            db.notebookDao().setLifecycle(id,"TRASHED",now()); db.workspaceContextDao().remove(id)
            db.memoryRecordDao().getRecord(id)?.let { supersedeProposals(it) }
        }; forgotten
    }
    private suspend fun supersedeProposals(record: MemoryRecordEntity) {
        val fingerprint="memory:" + MemoryProposalFingerprint.of(MemoryKind.valueOf(record.kind),MemoryScope.valueOf(record.scope),record.scopeId,record.content)
        db.personalMemoryDao().fingerprint(fingerprint)?.takeIf { it.state == "PENDING" }?.let { row ->
            db.personalMemoryDao().decide(row.id,"SUPERSEDED",now(),row.dismissedAt,row.neverAgain)
            db.notebookDao().setLifecycle(row.id,"ARCHIVED",now())
        }
    }
    override suspend fun contextCandidates(scopes: Set<MemoryScope>, scopeIds: Set<String>): List<MemoryRecord> =
        db.memoryRecordDao().recentScoped((scopes.ifEmpty { MemoryScope.entries.toSet() } - MemoryScope.CONVERSATION).map { it.name },scopeIds.take(20),now(),60).map { it.toDomain() }
    override suspend fun markUsed(ids: List<String>) { if(ids.isNotEmpty()) db.memoryRecordDao().used(ids.distinct().take(20),now()) }
    override fun observeRevisions(memoryId: String) = db.memoryRecordDao().observeRevisions(memoryId).map { rows -> rows.map { MemoryRevision(it.id,it.memoryId,it.previousContent,it.newContent,it.changedAt,it.reason) } }
    private fun MemoryRecordEntity.toDomain() = MemoryRecord(id,content,runCatching { MemoryKind.valueOf(kind) }.getOrDefault(MemoryKind.FACT),
        runCatching { MemoryScope.valueOf(scope) }.getOrDefault(MemoryScope.GLOBAL),scopeId,createdAt,updatedAt,sourceObjectId?.let(::WorkspaceObjectId),sourceRevision,confidence,
        runCatching { MemoryStatus.valueOf(status) }.getOrDefault(MemoryStatus.FORGOTTEN),runCatching { MemoryProposalOrigin.valueOf(origin) }.getOrDefault(MemoryProposalOrigin.IMPORT),
        PersonalMemoryCodec.decodeEvidence(evidenceJson),sourceEpisodeId,evidenceCount,lastConfirmedAt,usageCount,lastUsedAt,validUntil,userConfirmed)
}
