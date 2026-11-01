package com.halo.app.data.everyday

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "everyday_world_clocks")
data class WorldClockEntity(@PrimaryKey val id: String, val label: String, val zoneId: String, val cityId: String?, val position: Int, val version: Long, val deletedAt: Long?)
@Dao
interface WorldClockDao {
    @Query("SELECT * FROM everyday_world_clocks WHERE deletedAt IS NULL ORDER BY position,id") fun observe(): Flow<List<WorldClockEntity>>
    @Query("SELECT * FROM everyday_world_clocks WHERE deletedAt IS NULL ORDER BY position,id") suspend fun all(): List<WorldClockEntity>
    @Query("SELECT * FROM everyday_world_clocks WHERE id=:id") suspend fun get(id: String): WorldClockEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: WorldClockEntity)
    @Update suspend fun update(value: WorldClockEntity): Int
    @Query("DELETE FROM everyday_world_clocks") suspend fun clear()
    @Query("DELETE FROM everyday_world_clocks WHERE deletedAt IS NOT NULL AND deletedAt<:before") suspend fun prune(before: Long)
}
