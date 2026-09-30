package com.halo.app.data.payload

import androidx.room.*

@Entity(tableName = "payload_bodies")
data class PayloadBodyEntity(@PrimaryKey val sha256: String, val byteCount: Long, val createdAtMs: Long, val unreferencedAtMs: Long? = null)

@Entity(tableName = "payload_refs", primaryKeys = ["id", "ownerKind", "ownerId"], indices = [Index(value = ["ownerKind", "ownerId"]), Index("sha256")],
    foreignKeys = [ForeignKey(entity = PayloadBodyEntity::class, parentColumns = ["sha256"], childColumns = ["sha256"], onDelete = ForeignKey.RESTRICT)])
data class PayloadReferenceEntity(
    val id: String, val sha256: String, val ownerKind: String, val ownerId: String,
    val mediaType: String, val classification: String, val trust: String, val provenance: String,
    val remoteAllowed: Boolean, val createdAtMs: Long,
)
@Dao
interface PayloadDao {
    @Query("SELECT * FROM payload_bodies") suspend fun bodies(): List<PayloadBodyEntity>
    @Query("SELECT * FROM payload_refs") suspend fun references(): List<PayloadReferenceEntity>
    @Query("SELECT * FROM payload_refs WHERE id = :id AND ownerKind = :kind AND ownerId = :owner LIMIT 1")
    suspend fun reference(id: String, kind: String, owner: String): PayloadReferenceEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun body(value: PayloadBodyEntity)
    @Insert suspend fun reference(value: PayloadReferenceEntity)
    @Query("UPDATE payload_bodies SET unreferencedAtMs = NULL WHERE sha256 = :sha") suspend fun markReferenced(sha: String)
    @Query("UPDATE payload_bodies SET unreferencedAtMs = :now WHERE unreferencedAtMs IS NULL AND NOT EXISTS (SELECT 1 FROM payload_refs WHERE payload_refs.sha256 = payload_bodies.sha256)") suspend fun markUnreferenced(now: Long)
    @Query("DELETE FROM payload_refs WHERE ownerKind = :kind AND ownerId = :owner") suspend fun release(kind: String, owner: String)
    @Query("DELETE FROM payload_refs WHERE (ownerKind = 'AGENT_RUN' AND NOT EXISTS (SELECT 1 FROM agent_runs WHERE agent_runs.id = payload_refs.ownerId)) OR (ownerKind = 'CONVERSATION' AND NOT EXISTS (SELECT 1 FROM conversations WHERE conversations.id = payload_refs.ownerId)) OR (ownerKind = 'WORKSPACE' AND NOT EXISTS (SELECT 1 FROM workspace_objects WHERE workspace_objects.id = payload_refs.ownerId)) OR (ownerKind = 'MEMORY' AND NOT EXISTS (SELECT 1 FROM memory_records WHERE memory_records.id = payload_refs.ownerId))")
    suspend fun releaseMissingOwners()
    @Query("DELETE FROM payload_bodies WHERE sha256 = :sha AND NOT EXISTS (SELECT 1 FROM payload_refs WHERE sha256 = :sha)")
    suspend fun deleteUnreferenced(sha: String): Int
}
