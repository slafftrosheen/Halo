package com.halo.app.data.memory

import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class MemoryRecord(
    val id: String,
    val content: String,
    val kind: MemoryKind,
    val scope: MemoryScope,
    val scopeId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceObjectId: WorkspaceObjectId?,
    val sourceRevision: Int?,
    val confidence: Float?,
    val status: MemoryStatus
)

data class MemoryRevision(
    val id: String,
    val memoryId: String,
    val previousContent: String,
    val newContent: String,
    val changedAt: Long,
    val reason: String
)

data class MemoryCandidate(
    val content: String,
    val sourceObjectId: WorkspaceObjectId? = null,
    val sourceRevision: Int? = null,
    val kind: MemoryKind = MemoryKind.FACT,
    val scope: MemoryScope = MemoryScope.GLOBAL,
    val scopeId: String? = null
)

interface MemoryRepository {
    fun observeMemories(): Flow<List<MemoryRecord>>
    suspend fun getMemory(id: String): MemoryRecord?
    suspend fun promoteCandidate(candidate: MemoryCandidate): MemoryRecord
    suspend fun correctMemory(id: String, newContent: String, reason: String = "User correction"): Boolean
    suspend fun forgetMemory(id: String): Boolean
    fun observeRevisions(memoryId: String): Flow<List<MemoryRevision>>
}

class RoomMemoryRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : MemoryRepository {

    override fun observeMemories(): Flow<List<MemoryRecord>> {
        return db.memoryRecordDao().observeActiveRecords().map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getMemory(id: String): MemoryRecord? {
        return db.memoryRecordDao().getRecord(id)?.toDomain()
    }

    override suspend fun promoteCandidate(candidate: MemoryCandidate): MemoryRecord {
        val timestamp = now()
        val id = newId()
        val entity = MemoryRecordEntity(
            id = id,
            content = candidate.content.trim(),
            kind = candidate.kind.name,
            scope = candidate.scope.name,
            scopeId = candidate.scopeId,
            createdAt = timestamp,
            updatedAt = timestamp,
            sourceObjectId = candidate.sourceObjectId?.value,
            sourceRevision = candidate.sourceRevision,
            confidence = 1.0f,
            status = MemoryStatus.ACTIVE.name
        )
        db.memoryRecordDao().insertRecord(entity)
        return requireNotNull(getMemory(id))
    }

    override suspend fun correctMemory(id: String, newContent: String, reason: String): Boolean {
        val current = getMemory(id) ?: return false
        val timestamp = now()
        if (current.content == newContent.trim()) return true

        val revisionEntity = MemoryRevisionEntity(
            id = newId(),
            memoryId = id,
            previousContent = current.content,
            newContent = newContent.trim(),
            changedAt = timestamp,
            reason = reason
        )
        db.memoryRecordDao().insertRevision(revisionEntity)
        db.memoryRecordDao().updateContent(id, newContent.trim(), timestamp)
        return true
    }

    override suspend fun forgetMemory(id: String): Boolean {
        return db.memoryRecordDao().forget(id, now()) > 0
    }

    override fun observeRevisions(memoryId: String): Flow<List<MemoryRevision>> {
        return db.memoryRecordDao().observeRevisions(memoryId).map { entities ->
            entities.map { entity ->
                MemoryRevision(
                    id = entity.id,
                    memoryId = entity.memoryId,
                    previousContent = entity.previousContent,
                    newContent = entity.newContent,
                    changedAt = entity.changedAt,
                    reason = entity.reason
                )
            }
        }
    }

    private fun MemoryRecordEntity.toDomain() = MemoryRecord(
        id = id,
        content = content,
        kind = runCatching { MemoryKind.valueOf(kind) }.getOrDefault(MemoryKind.FACT),
        scope = runCatching { MemoryScope.valueOf(scope) }.getOrDefault(MemoryScope.GLOBAL),
        scopeId = scopeId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceObjectId = sourceObjectId?.let { WorkspaceObjectId(it) },
        sourceRevision = sourceRevision,
        confidence = confidence,
        status = runCatching { MemoryStatus.valueOf(status) }.getOrDefault(MemoryStatus.ACTIVE)
    )
}
