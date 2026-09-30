package com.halo.app.data.notification

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE `key` = :key AND active = 1 LIMIT 1")
    suspend fun activeByKey(key: String): NotificationEntity?
    @Upsert suspend fun upsert(entity: NotificationEntity)
    @Query("UPDATE notifications SET active = 0, removedAt = :removedAt WHERE `key` = :key")
    suspend fun markRemoved(key: String, removedAt: Long)
    @Query("SELECT * FROM notifications WHERE active = 1 ORDER BY postedAt DESC LIMIT 500")
    fun observeActive(): Flow<List<NotificationEntity>>
    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<NotificationEntity>>
    @Query("SELECT * FROM notifications WHERE postedAt >= :since ORDER BY postedAt DESC LIMIT :limit")
    suspend fun recentSince(since: Long, limit: Int): List<NotificationEntity>
    @Query("DELETE FROM notifications WHERE postedAt < :timestamp") suspend fun deleteOlderThan(timestamp: Long)
    @Query("DELETE FROM notifications WHERE packageName = :pkg") suspend fun deletePackage(pkg: String)
    @Query("UPDATE notifications SET title = NULL, text = NULL WHERE packageName = :pkg") suspend fun redactPackage(pkg: String)
    @Query("DELETE FROM notifications WHERE `key` = :key") suspend fun deleteKey(key: String)
    @Query("UPDATE notifications SET active = 0, removedAt = :at WHERE active = 1 AND `key` NOT IN (:keys)")
    suspend fun reconcileActive(keys: List<String>, at: Long)
    @Query("SELECT DISTINCT packageName FROM notifications") suspend fun packages(): List<String>
    @Query("DELETE FROM notifications") suspend fun clear()
}
