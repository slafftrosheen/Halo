package com.halo.app.data.agent

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechOutputFallback
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant

/** v10 stores run metadata in a versioned step envelope, without changing its published schema. */
class RoomAgentRunRepository(private val db: AppDatabase) : AgentRunStore {
    private val dao get() = db.agentRunDao()
    override fun observeRuns() = dao.observeRuns().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(runId: AgentRunId) = dao.get(runId.value)?.toDomain()
    override suspend fun interruptedRuns() = dao.interruptedRuns().map { it.toDomain() }
    override suspend fun create(run: AgentRun) = db.withTransaction {
        dao.insertRun(run.toEntity())
        val metadata = JSONObject().put("createdAt", run.createdAt.toEpochMilli())
            .put("originSource", run.origin?.source?.javaClass?.simpleName)
            .put("originHost", run.origin?.hostId)
            .put("outputDevice", (run.speechOutputTarget as? SpeechOutputTarget.Wearable)?.deviceId)
            .put("outputName", (run.speechOutputTarget as? SpeechOutputTarget.Wearable)?.displayName)
            .put("phoneOutput", run.speechOutputTarget == SpeechOutputTarget.Phone)
            .put("fallback", run.speechOutputFallback.name)
            .put("sourceCaptureId", run.sourceCaptureId)
        dao.insertStep(AgentStepEntity("${run.id.value}:metadata", run.id.value, run.interactionId.value,
            0, "RunMetadata", 3, metadata.toString(), null, "RECORDED", run.createdAt.toEpochMilli(), null))
        run.steps.forEach { appendStep(it) }
    }
    override suspend fun update(run: AgentRun) {
        check(dao.updateRun(run.toEntity()) == 1) { "Run does not exist" }
    }
    override suspend fun appendStep(step: AgentStep): Boolean = db.withTransaction {
        val row = requireNotNull(dao.get(step.runId.value)) { "Run does not exist" }
        check(!row.toDomain().state.isTerminal) { "Run is terminal" }
        val key = (step as? AgentStep.ToolDispatched)?.idempotencyKey
        if (row.steps.any { it.id == step.id.value } || (key != null && dao.countDispatchKey(key) > 0)) {
            return@withTransaction false
        }
        val payload = JSONObject()
        val type = when (step) {
            is AgentStep.PlanCreated -> { payload.put("planId", step.planId).put("summary", step.summary); "PlanCreated" }
            is AgentStep.ClarificationRequested -> { payload.put("question", step.question).put("field", step.field); "ClarificationRequested" }
            is AgentStep.ApprovalRequested -> { payload.put("action", step.action).put("risk", step.risk); "ApprovalRequested" }
            is AgentStep.ToolRequested -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("argumentsJson", step.argumentsJson); "ToolRequested" }
            is AgentStep.ToolDispatched -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId); "ToolDispatched" }
            is AgentStep.ToolCompleted -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("resultSummary", step.resultSummary).put("success", step.success).put("actionTruth", step.actionTruth.name); "ToolCompleted" }
            is AgentStep.ToolFailed -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("reason", step.reason); "ToolFailed" }
            is AgentStep.ResponseProduced -> { payload.put("responseId", step.responseId).put("text", step.text); "ResponseProduced" }
        }
        val truth = when (step) {
            is AgentStep.ToolDispatched -> ActionTruth.DISPATCHED
            is AgentStep.ToolCompleted -> step.actionTruth
            is AgentStep.ToolFailed -> ActionTruth.FAILED
            else -> ActionTruth.PROPOSED
        }
        dao.insertStep(AgentStepEntity(step.id.value, step.runId.value, row.run.interactionId,
            (row.steps.maxOfOrNull { it.ordinal } ?: -1) + 1, type, 3, payload.toString(), key,
            truth.name, step.createdAt.toEpochMilli(), step.createdAt.toEpochMilli().takeIf {
                step is AgentStep.ToolCompleted || step is AgentStep.ToolFailed || step is AgentStep.ResponseProduced
            }))
        dao.updateRun(row.run.copy(updatedAt = maxOf(row.run.updatedAt, step.createdAt.toEpochMilli())))
        true
    }

    private fun AgentRun.toEntity() = AgentRunEntity(id.value, interactionId.value, request,
        (startedAt ?: createdAt).toEpochMilli(), updatedAt.toEpochMilli(),
        completedAt?.toEpochMilli().takeIf { state.isTerminal }, state.name, failureReason)

    private fun AgentRunWithSteps.toDomain(): AgentRun {
        val ordered = steps.sortedWith(compareBy<AgentStepEntity> { it.ordinal }.thenBy { it.createdAt }.thenBy { it.id })
        val metadata = ordered.firstOrNull { it.type == "RunMetadata" }?.let { runCatching { JSONObject(it.payloadJson) }.getOrNull() }
        val source = when (metadata?.optString("originSource")) {
            "PhoneCamera" -> ObservationSource.PhoneCamera
            "AndroidPhoneMic" -> ObservationSource.AndroidPhoneMic
            "BluetoothHeadset" -> ObservationSource.BluetoothHeadset
            "HeyCyanGlasses" -> ObservationSource.HeyCyanGlasses
            "Replay" -> ObservationSource.Replay
            "Debug" -> ObservationSource.Debug
            else -> null
        }
        val history = ordered.filterNot { it.type == "RunMetadata" }.map { entity ->
            runCatching { entity.toDomain() }.getOrElse {
                AgentStep.ToolFailed(AgentStepId(entity.id), AgentRunId(entity.runId), Instant.ofEpochMilli(entity.createdAt),
                    entity.id, "legacy", "Historical execution evidence could not be decoded")
            }
        }
        val unrecognized = history.filterIsInstance<AgentStep.ToolFailed>().any { it.toolId == "legacy" }
        val state = if (unrecognized) AgentRunState.INTERRUPTED else
            runCatching { AgentRunState.valueOf(run.state) }.getOrDefault(AgentRunState.INTERRUPTED)
        return AgentRun(AgentRunId(run.id), InteractionId(run.interactionId), run.request, state,
            source?.let { ObservationOrigin(it, metadata?.optString("originHost")?.takeIf(String::isNotBlank)) },
            Instant.ofEpochMilli(metadata?.optLong("createdAt", run.startedAt) ?: run.startedAt),
            Instant.ofEpochMilli(run.updatedAt), Instant.ofEpochMilli(run.startedAt),
            run.completedAt?.let(Instant::ofEpochMilli), history.lastOrNull()?.id, run.failureReason, history,
            metadata?.optString("outputDevice")?.takeIf(String::isNotBlank)?.let {
                SpeechOutputTarget.Wearable(it, metadata?.optString("outputName", "Wearable") ?: "Wearable")
            } ?: SpeechOutputTarget.Phone.takeIf { metadata?.optBoolean("phoneOutput") == true },
            runCatching { SpeechOutputFallback.valueOf(metadata?.optString("fallback").orEmpty()) }.getOrDefault(SpeechOutputFallback.DISALLOW_PHONE),
            metadata?.optString("sourceCaptureId")?.takeIf(String::isNotBlank))
    }

    private fun AgentStepEntity.toDomain(): AgentStep {
        val p = JSONObject(payloadJson)
        val step = AgentStepId(id)
        val run = AgentRunId(runId)
        val at = Instant.ofEpochMilli(createdAt)
        val call = p.optString("toolCallId", id)
        val tool = p.optString("toolId", p.optString("toolName"))
        return when (type) {
            "PlanCreated" -> AgentStep.PlanCreated(step, run, at, p.getString("planId"), p.getString("summary"))
            "ClarificationRequested" -> AgentStep.ClarificationRequested(step, run, at, p.getString("question"), p.getString("field"))
            "ApprovalRequested" -> AgentStep.ApprovalRequested(step, run, at, p.getString("action"), p.getString("risk"))
            "ToolRequested" -> AgentStep.ToolRequested(step, run, at, call, tool, p.optString("argumentsJson", p.optJSONObject("args")?.toString() ?: "{}"))
            "ToolDispatched" -> AgentStep.ToolDispatched(step, run, at, call, tool, idempotencyKey)
            "ToolCompleted" -> AgentStep.ToolCompleted(step, run, at, call, tool, p.optString("resultSummary", p.optString("result")), p.getBoolean("success"), runCatching { ActionTruth.valueOf(p.optString("actionTruth", "UNKNOWN")) }.getOrDefault(ActionTruth.UNKNOWN))
            "ToolFailed" -> AgentStep.ToolFailed(step, run, at, call, tool, p.getString("reason"))
            "ResponseProduced" -> AgentStep.ResponseProduced(step, run, at, p.getString("responseId"), p.getString("text"))
            // Legacy reasoning is deliberately not exposed as public execution evidence.
            else -> AgentStep.ToolFailed(step, run, at, id, "legacy", "Unsupported historical step: $type")
        }
    }
}
