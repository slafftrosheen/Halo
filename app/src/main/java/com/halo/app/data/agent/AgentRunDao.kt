package com.halo.app.data.agent

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class AgentRunWithSteps(
    @Embedded val run: AgentRunEntity,
    @Relation(parentColumn = "id", entityColumn = "runId") val steps: List<AgentStepEntity>,
)

@Dao
interface AgentRunDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(entity: AgentRunEntity)
    @Update
    suspend fun updateRun(entity: AgentRunEntity): Int
    @Query("SELECT COUNT(*) FROM agent_steps WHERE idempotencyKey = :key")
    suspend fun countDispatchKey(key: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStep(entity: AgentStepEntity)
    @Transaction
    @Query("SELECT * FROM agent_runs ORDER BY startedAt DESC")
    fun observeRuns(): Flow<List<AgentRunWithSteps>>
    @Transaction
    @Query("SELECT * FROM agent_runs WHERE id = :id")
    suspend fun get(id: String): AgentRunWithSteps?
    @Transaction
    @Query("SELECT * FROM agent_runs WHERE state NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED')")
    suspend fun interruptedRuns(): List<AgentRunWithSteps>
    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY ordinal ASC, createdAt ASC")
    fun observeStepsForRun(runId: String): Flow<List<AgentStepEntity>>
}
