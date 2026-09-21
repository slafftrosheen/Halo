package com.halo.app.data.agent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(entity: AgentRunEntity)

    @Query("UPDATE agent_runs SET state = :state, completedAt = :completedAt WHERE id = :id")
    suspend fun updateRunState(id: String, state: String, completedAt: Long?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(entity: AgentStepEntity)

    @Query("SELECT * FROM agent_runs ORDER BY startedAt DESC")
    fun observeRuns(): Flow<List<AgentRunEntity>>

    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY timestamp ASC")
    fun observeStepsForRun(runId: String): Flow<List<AgentStepEntity>>
}
