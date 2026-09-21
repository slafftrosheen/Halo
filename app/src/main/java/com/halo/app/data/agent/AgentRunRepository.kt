package com.halo.app.data.agent

import com.halo.app.data.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

data class AgentRun(
    val id: String,
    val request: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val state: AgentRunState = AgentRunState.PLANNING,
    val steps: List<AgentStep> = emptyList()
)

sealed interface AgentStep {
    val id: String
    val runId: String
    val timestamp: Long

    data class Reasoning(
        override val id: String,
        override val runId: String,
        override val timestamp: Long,
        val summary: String
    ) : AgentStep

    data class ToolRequested(
        override val id: String,
        override val runId: String,
        override val timestamp: Long,
        val toolName: String,
        val arguments: Map<String, Any?>
    ) : AgentStep

    data class ToolCompleted(
        override val id: String,
        override val runId: String,
        override val timestamp: Long,
        val toolName: String,
        val result: String,
        val success: Boolean
    ) : AgentStep
}

interface AgentRunRepository {
    fun observeRuns(): Flow<List<AgentRun>>
    suspend fun startRun(request: String): AgentRun
    suspend fun addStep(step: AgentStep)
    suspend fun completeRun(runId: String, state: AgentRunState)
}

class RoomAgentRunRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : AgentRunRepository {

    override fun observeRuns(): Flow<List<AgentRun>> {
        return db.agentRunDao().observeRuns().map { entities ->
            entities.map { entity ->
                val state = runCatching { AgentRunState.valueOf(entity.state) }.getOrDefault(AgentRunState.COMPLETED)
                AgentRun(
                    id = entity.id,
                    request = entity.request,
                    startedAt = entity.startedAt,
                    completedAt = entity.completedAt,
                    state = state
                )
            }
        }
    }

    override suspend fun startRun(request: String): AgentRun {
        val id = newId()
        val timestamp = now()
        val entity = AgentRunEntity(
            id = id,
            request = request,
            startedAt = timestamp,
            completedAt = null,
            state = AgentRunState.EXECUTING.name
        )
        db.agentRunDao().insertRun(entity)
        return AgentRun(id = id, request = request, startedAt = timestamp, state = AgentRunState.EXECUTING)
    }

    override suspend fun addStep(step: AgentStep) {
        val (type, payload) = when (step) {
            is AgentStep.Reasoning -> "Reasoning" to JSONObject(mapOf("summary" to step.summary)).toString()
            is AgentStep.ToolRequested -> "ToolRequested" to JSONObject(mapOf("toolName" to step.toolName, "args" to step.arguments)).toString()
            is AgentStep.ToolCompleted -> "ToolCompleted" to JSONObject(mapOf("toolName" to step.toolName, "result" to step.result, "success" to step.success)).toString()
        }
        val entity = AgentStepEntity(
            id = step.id,
            runId = step.runId,
            stepType = type,
            payload = payload,
            timestamp = step.timestamp
        )
        db.agentRunDao().insertStep(entity)
    }

    override suspend fun completeRun(runId: String, state: AgentRunState) {
        db.agentRunDao().updateRunState(runId, state.name, now())
    }
}
