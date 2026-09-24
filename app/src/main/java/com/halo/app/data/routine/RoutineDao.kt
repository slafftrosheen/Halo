package com.halo.app.data.routine

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC") fun observeRoutines(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines") suspend fun all(): List<RoutineEntity>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun get(id: String): RoutineEntity?
    @Upsert suspend fun upsert(entity: RoutineEntity)
    @Query("UPDATE routines SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
    @Query("UPDATE routines SET nextRunAt = :at WHERE id = :id") suspend fun nextRun(id: String, at: Long?)
    @Query("UPDATE routines SET lastRunAt = :at WHERE id = :id") suspend fun lastRun(id: String, at: Long)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRun(run: RoutineRunEntity): Long
    @Query("SELECT * FROM routine_runs WHERE id = :id") suspend fun run(id: String): RoutineRunEntity?
    @Query("SELECT * FROM routine_runs WHERE routineId = :routineId ORDER BY triggerAt DESC LIMIT 100")
    fun observeRuns(routineId: String): Flow<List<RoutineRunEntity>>
    @Query("SELECT * FROM routine_runs ORDER BY triggerAt DESC LIMIT 100") fun observeRecentRuns(): Flow<List<RoutineRunEntity>>
    @Query("UPDATE routine_runs SET state = :state WHERE id = :runId") suspend fun updateRunState(runId: String, state: String)
    @Query("SELECT * FROM routine_runs ORDER BY triggerAt DESC LIMIT :limit") suspend fun recentRuns(limit: Int): List<RoutineRunEntity>
    @Query("""UPDATE routine_runs SET state = CASE
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'COMPLETED' THEN 'COMPLETED'
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'FAILED' THEN 'FAILED'
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'INTERRUPTED' END WHERE state IN ('RUNNING', 'SCHEDULED')""")
    suspend fun interruptUnfinished()
}
