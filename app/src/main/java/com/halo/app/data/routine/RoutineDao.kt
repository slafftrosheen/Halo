package com.halo.app.data.routine

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ManualRoutineCount(val routineId: String,val count: Int)

@Dao
interface RoutineDao {
    @Query("SELECT routineId, COUNT(*) AS count FROM routine_runs WHERE source = 'MANUAL' AND state = 'COMPLETED' AND triggerAt >= :since GROUP BY routineId ORDER BY count DESC LIMIT 10")
    suspend fun recentManualPatterns(since: Long): List<ManualRoutineCount>

    @Query("SELECT * FROM routines ORDER BY createdAt DESC") fun observeRoutines(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines") suspend fun all(): List<RoutineEntity>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun get(id: String): RoutineEntity?
    @Upsert suspend fun upsert(entity: RoutineEntity)
    @Query("UPDATE routines SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
    @Query("UPDATE routines SET nextRunAt = :at WHERE id = :id") suspend fun nextRun(id: String, at: Long?)
    @Query("UPDATE routines SET lastRunAt = :at WHERE id = :id") suspend fun lastRun(id: String, at: Long)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRun(run: RoutineRunEntity): Long
    @Query("SELECT * FROM routine_runs WHERE id = :id") suspend fun run(id: String): RoutineRunEntity?
    @Query("SELECT * FROM routine_runs WHERE routineId = :routineId ORDER BY triggerAt DESC LIMIT 100")
    fun observeRuns(routineId: String): Flow<List<RoutineRunEntity>>
    // Keep the last useful result even when the recent window contains only failures.
    @Query("""SELECT r.* FROM routine_runs r WHERE
        r.id IN (SELECT id FROM routine_runs ORDER BY triggerAt DESC, id DESC LIMIT 100)
        OR r.id IN (SELECT MAX(success.id) FROM routine_runs success
            INNER JOIN (SELECT routineId, MAX(triggerAt) AS latestAt FROM routine_runs
                WHERE state = 'COMPLETED' AND source != 'TRIAL' GROUP BY routineId) latest
            ON success.routineId = latest.routineId AND success.triggerAt = latest.latestAt
            WHERE success.state = 'COMPLETED' AND success.source != 'TRIAL' GROUP BY success.routineId)
        ORDER BY r.triggerAt DESC, r.id DESC""")
    fun observeRecentRuns(): Flow<List<RoutineRunEntity>>
    @Query("UPDATE routine_runs SET state = :state, actionsCompleted = :completed, actionsTotal = :total, finishedAt = :at, reason = :reason WHERE id = :id")
    suspend fun finishRun(id: String, state: String, completed: Int, total: Int, at: Long, reason: String)
    @Query("UPDATE routine_runs SET state = :state WHERE id = :runId") suspend fun updateRunState(runId: String, state: String)
    @Query("UPDATE routine_runs SET actionsCompleted = :completed, actionsTotal = :total, finishedAt = :at, reason = :reason WHERE id = :id")
    suspend fun runEvidence(id: String, completed: Int, total: Int, at: Long, reason: String)
    @Query("UPDATE routine_runs SET coalescedCount = MIN(coalescedCount + 1, 1000) WHERE id = (SELECT id FROM routine_runs WHERE routineId = :routineId AND source = 'EVENT' ORDER BY triggerAt DESC LIMIT 1)")
    suspend fun coalesceLatest(routineId: String)
    @Query("UPDATE routine_runs SET deliveryOutcome = :outcome WHERE id = :id")
    suspend fun deliveryEvidence(id: String, outcome: String)
    @Query("SELECT * FROM routine_runs ORDER BY triggerAt DESC LIMIT :limit") suspend fun recentRuns(limit: Int): List<RoutineRunEntity>
    @Query("""UPDATE routine_runs SET state = CASE
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'COMPLETED' THEN 'COMPLETED'
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'FAILED' THEN 'FAILED'
        WHEN (SELECT state FROM agent_runs WHERE id = routine_runs.agentRunId) = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'INTERRUPTED' END WHERE state IN ('RUNNING', 'SCHEDULED')""")
    suspend fun interruptUnfinished()
}
