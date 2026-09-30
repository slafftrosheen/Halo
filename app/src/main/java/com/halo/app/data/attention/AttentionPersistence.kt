package com.halo.app.data.attention

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "attention_pending", indices = [Index(value = ["dedupKey"], unique = true)])
data class AttentionPendingEntity(@PrimaryKey val id: String, val dedupKey: String?, val json: String, val createdAt: Long)
@Entity(tableName = "attention_receipts")
data class AttentionReceiptEntity(@PrimaryKey val id: String, val outcome: String, val channel: String?, val reason: String?,
    val at: Long, val dedupKey: String?, val disruptive: Boolean)
@Dao interface AttentionDao {
    @Query("SELECT * FROM attention_pending ORDER BY createdAt LIMIT 50") fun observe(): Flow<List<AttentionPendingEntity>>
    @Query("SELECT COUNT(*) FROM attention_pending") suspend fun count(): Int
    @Query("DELETE FROM attention_pending WHERE id = :id OR (:dedup IS NOT NULL AND dedupKey = :dedup)") suspend fun removeDuplicates(id: String, dedup: String?)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: AttentionPendingEntity)
    @Query("DELETE FROM attention_pending WHERE id = :id") suspend fun remove(id: String)
    @Query("SELECT * FROM attention_receipts WHERE id = :id") suspend fun receipt(id: String): AttentionReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun claim(receipt: AttentionReceiptEntity): Long
    @Upsert suspend fun receiptPut(receipt: AttentionReceiptEntity)
    @Query("SELECT * FROM attention_receipts ORDER BY at DESC LIMIT 200") fun observeReceipts(): Flow<List<AttentionReceiptEntity>>
    @Query("SELECT * FROM attention_receipts ORDER BY at DESC LIMIT 200") suspend fun history(): List<AttentionReceiptEntity>
    @Query("SELECT * FROM attention_receipts WHERE disruptive = 1 AND at >= :since ORDER BY at DESC LIMIT 1000") suspend fun dispatches(since: Long): List<AttentionReceiptEntity>
    @Query("DELETE FROM attention_receipts WHERE at < :before AND id NOT IN (SELECT id FROM attention_pending)") suspend fun prune(before: Long)
}
