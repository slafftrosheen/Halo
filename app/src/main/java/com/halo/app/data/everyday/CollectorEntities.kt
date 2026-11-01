package com.halo.app.data.everyday

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "collector_sources")
data class CollectorSourceEntity(@PrimaryKey val id: String, val version: Long, val body: String)

@Entity(tableName = "collector_snapshots", foreignKeys = [ForeignKey(entity = CollectorSourceEntity::class,
    parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)])
data class CollectorSnapshotEntity(@PrimaryKey val sourceId: String, val sourceVersion: Long, val contentRevision: String,
    val retrievedAt: Long, val verifiedAt: Long, val expiresAt: Long, val payload: String,
    val etag: String?, val lastModified: String?, val effectiveUrl: String?)

@Entity(tableName = "collector_attempts", foreignKeys = [ForeignKey(entity = CollectorSourceEntity::class,
    parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)])
data class CollectorAttemptEntity(@PrimaryKey val sourceId: String, val sourceVersion: Long, val token: String?, val at: Long?,
    val state: String, val consecutiveFailures: Int, val nextAllowedAt: Long?, val message: String?)

/** Reservations count against a rolling day even if a server fails or a source is removed and re-added. */
@Entity(tableName = "collector_requests", indices = [Index(value = ["at"]), Index(value = ["sourceId", "at"])])
data class CollectorRequestEntity(@PrimaryKey val token: String, val sourceId: String, val at: Long)

@Entity(tableName = "collector_endpoint_state")
data class CollectorEndpointEntity(@PrimaryKey val id: String, val attemptedAt: Long, val blockedUntil: Long?, val failures: Int)

@Dao
interface CollectorDao {
    @Query("SELECT * FROM collector_endpoint_state WHERE id=:id") suspend fun endpoint(id: String): CollectorEndpointEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEndpoint(value: CollectorEndpointEntity)
    @Query("DELETE FROM collector_endpoint_state WHERE attemptedAt<:before AND (blockedUntil IS NULL OR blockedUntil<:before)") suspend fun pruneEndpoints(before: Long)
    @Query("SELECT MAX(at) FROM collector_requests WHERE sourceId=:id") suspend fun latestRequest(id: String): Long?
    // Joins make Room invalidate this projection when source, snapshot OR attempt changes.
    @Query("SELECT s.* FROM collector_sources s LEFT JOIN collector_snapshots c ON c.sourceId=s.id LEFT JOIN collector_attempts a ON a.sourceId=s.id ORDER BY s.id")
    fun observe(): Flow<List<CollectorSourceEntity>>
    @Query("SELECT * FROM collector_sources ORDER BY id") suspend fun sources(): List<CollectorSourceEntity>
    @Query("SELECT * FROM collector_sources WHERE id=:id") suspend fun source(id: String): CollectorSourceEntity?
    @Insert suspend fun insert(source: CollectorSourceEntity)
    @Update suspend fun update(source: CollectorSourceEntity): Int
    @Query("DELETE FROM collector_sources WHERE id=:id AND version=:version") suspend fun remove(id: String, version: Long): Int
    @Query("SELECT * FROM collector_snapshots WHERE sourceId=:id") suspend fun snapshot(id: String): CollectorSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSnapshot(value: CollectorSnapshotEntity)
    @Query("DELETE FROM collector_snapshots WHERE sourceId=:id") suspend fun removeSnapshot(id: String)
    @Query("SELECT * FROM collector_attempts WHERE sourceId=:id") suspend fun attempt(id: String): CollectorAttemptEntity?
    @Query("SELECT * FROM collector_attempts WHERE state='FETCHING'") suspend fun interrupted(): List<CollectorAttemptEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAttempt(value: CollectorAttemptEntity)
    @Query("SELECT COUNT(*) FROM collector_requests WHERE at>=:since") suspend fun requestCount(since: Long): Int
    @Query("SELECT COUNT(*) FROM collector_requests WHERE sourceId=:id AND at>=:since") suspend fun sourceRequestCount(id: String, since: Long): Int
    @Query("SELECT MIN(at) FROM collector_requests WHERE at>=:since") suspend fun oldestRequest(since: Long): Long?
    @Query("SELECT MIN(at) FROM collector_requests WHERE sourceId=:id AND at>=:since") suspend fun oldestSourceRequest(id: String, since: Long): Long?
    @Insert suspend fun request(value: CollectorRequestEntity)
    @Query("DELETE FROM collector_requests WHERE at<:before") suspend fun pruneRequests(before: Long)
}
