package com.halo.app.data.agent

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolId
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechOutputFallback
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * RUNTIME-018: Room adapter for the durable execution journal.
 *
 * New journal step types use step schemaVersion 5. Historical versions (<=4)
 * decode unchanged; unknown newer versions decode as conservative legacy
 * evidence without inventing fields. `ToolDispatched` decodes as the legacy
 * claim it always was.
 */
class RoomAgentRunRepository(private val db: AppDatabase) : AgentRunStore {
    private val dao get() = db.agentRunDao()
    override fun observeRuns() = dao.observeRuns().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(runId: AgentRunId) = dao.get(runId.value)?.toDomain()
    override suspend fun nonTerminalRuns() = dao.nonTerminalRuns().map { it.toDomain() }
    @Deprecated("Returns every non-terminal run; renamed for honesty")
    override suspend fun interruptedRuns() = nonTerminalRuns()

    override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean =
        dao.claimResume(runId.value, expectedState.name, System.currentTimeMillis()) == 1

    override suspend fun claimRecovery(
        runId: AgentRunId,
        expectedState: AgentRunState,
        claim: AgentStep.RecoveryClaimed,
    ): Boolean = db.withTransaction {
        if (dao.claimResume(runId.value, expectedState.name, System.currentTimeMillis()) != 1) {
            return@withTransaction false
        }
        check(appendStep(claim)) { "Recovery claim fingerprint already exists" }
        true
    }

    /**
     * RUNTIME-019: atomic first-wins input resolution. A single Room
     * transaction re-checks that the request is still pending (latest
     * InputRequested with no matching InputResolved) before appending the
     * resolution, so concurrent hosts can never double-resolve.
     */
    override suspend fun resolveInputIfPending(
        requestId: AgentInputRequestId,
        resolution: AgentInputResolution,
    ): Boolean = db.withTransaction {
        val runId = dao.latestInputRequestedRunId("%\"requestId\":\"${requestId.value}\"%")
            ?: return@withTransaction false
        val row = dao.get(runId) ?: return@withTransaction false
        val run = row.toDomain()
        if (run.state.isTerminal) return@withTransaction false
        val pending = run.steps.fold(mutableSetOf<String>()) { acc, step ->
            when (step) {
                is AgentStep.InputRequested -> if (step.requestId == requestId) acc.add(step.requestId.value)
                is AgentStep.InputResolved -> acc.remove(step.requestId.value)
                else -> Unit
            }
            acc
        }
        if (requestId.value !in pending) return@withTransaction false
        appendStep(
            AgentStep.InputResolved(
                AgentStepId(UUID.randomUUID().toString()), run.id, Instant.now(),
                requestId, AgentInputResolutions.encode(resolution),
            )
        )
        true
    }

    override suspend fun create(run: AgentRun) = db.withTransaction {
        dao.insertRun(run.toEntity())
        val metadata = JSONObject().put("createdAt", run.createdAt.toEpochMilli())
            .put("originSource", run.origin?.source?.javaClass?.simpleName)
            .put("originHost", run.origin?.hostId)
            .put("outputDevice", (run.speechOutputTarget as? SpeechOutputTarget.Device)?.deviceId)
            .put("outputName", (run.speechOutputTarget as? SpeechOutputTarget.Device)?.displayName)
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
        val (payload, type) = payloadFor(step)
        val key = when (step) {
            is AgentStep.ToolDispatched -> step.idempotencyKey
            is AgentStep.ToolExecutionClaimed -> step.idempotencyKey
            is AgentStep.ToolInvocationPrepared -> step.idempotencyKey
            else -> null
        }
        if (row.steps.any { it.id == step.id.value } ||
            (key != null && dao.countIdempotencyKeyForType(key, type) > 0)
        ) {
            return@withTransaction false
        }
        val truth = when (step) {
            is AgentStep.ToolDispatched -> ActionTruth.DISPATCHED
            is AgentStep.ToolExecutionClaimed -> ActionTruth.AUTHORIZED
            is AgentStep.ToolOutcomeRecorded -> step.actionTruth
            is AgentStep.ToolCompleted -> step.actionTruth
            is AgentStep.ToolFailed, is AgentStep.ToolRejected -> ActionTruth.FAILED
            else -> ActionTruth.PROPOSED
        }
        val completedAt = step.createdAt.toEpochMilli().takeIf {
            step is AgentStep.ToolCompleted || step is AgentStep.ToolFailed || step is AgentStep.ToolRejected ||
                step is AgentStep.ToolOutcomeRecorded || step is AgentStep.ResponseProduced ||
                step is AgentStep.ProviderTurnCompleted || step is AgentStep.ResponseCommitted
        }
        dao.insertStep(AgentStepEntity(step.id.value, step.runId.value, row.run.interactionId,
            (row.steps.maxOfOrNull { it.ordinal } ?: -1) + 1, type, STEP_SCHEMA_VERSION, payload.toString(), key,
            truth.name, step.createdAt.toEpochMilli(), completedAt))
        dao.updateRun(row.run.copy(updatedAt = maxOf(row.run.updatedAt, step.createdAt.toEpochMilli())))
        true
    }

    private fun payloadFor(step: AgentStep): Pair<JSONObject, String> {
        val payload = JSONObject()
        val type = when (step) {
            is AgentStep.PlanCreated -> { payload.put("planId", step.planId).put("summary", step.summary); "PlanCreated" }
            is AgentStep.ClarificationRequested -> { payload.put("question", step.question).put("field", step.field); "ClarificationRequested" }
            is AgentStep.ApprovalRequested -> { payload.put("action", step.action).put("risk", step.risk); "ApprovalRequested" }
            is AgentStep.ToolRequested -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("argumentsJson", step.argumentsJson); "ToolRequested" }
            is AgentStep.ToolDispatched -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId); "ToolDispatched" }
            is AgentStep.ToolCompleted -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("resultSummary", step.resultSummary).put("success", step.success).put("actionTruth", step.actionTruth.name); "ToolCompleted" }
            is AgentStep.ToolFailed -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("reason", step.reason); "ToolFailed" }
            is AgentStep.ToolRejected -> { payload.put("toolCallId", step.toolCallId).put("toolId", step.toolId).put("reasonCode", step.reasonCode.name).put("reason", step.reason).put("retryable", step.retryable).put("requiresUserAction", step.requiresUserAction).put("modelResult", step.modelResult); "ToolRejected" }
            is AgentStep.ResponseProduced -> { payload.put("responseId", step.responseId).put("text", step.text); "ResponseProduced" }

            is AgentStep.RunPrepared -> { payload.put("kind", step.kind.name).put("recoveryPolicy", step.recoveryPolicy.name); "RunPrepared" }
            is AgentStep.ProviderTurnStarted -> {
                payload.put("turnId", step.turnId.value).put("providerId", step.providerId)
                    .put("modelId", step.modelId ?: "").put("requestIdentity", step.requestIdentity)
                    .put("exposedToolIds", JSONArray(step.exposedToolIds.toList()))
                    .put("requiredCapabilities", JSONArray(step.requiredCapabilities.toList()))
                    .put("contextRequirements", JSONArray(step.contextRequirements.toList()))
                "ProviderTurnStarted"
            }
            is AgentStep.ProviderTurnCompleted -> {
                val calls = JSONArray()
                step.toolCalls.forEach { call ->
                    calls.put(JSONObject().put("toolCallId", call.toolCallId)
                        .put("toolName", call.toolName).put("argumentsJson", call.argumentsJson))
                }
                payload.put("turnId", step.turnId.value).put("assistantText", step.assistantText)
                    .put("toolCalls", calls).put("finishReason", step.finishReason)
                "ProviderTurnCompleted"
            }
            is AgentStep.ToolInvocationPrepared -> {
                payload.put("invocationId", step.invocationId.value).put("toolCallId", step.toolCallId)
                    .put("toolId", step.toolId).put("argumentsJson", step.argumentsJson)
                    .put("argumentsDigest", step.argumentsDigest).put("idempotencyKey", step.idempotencyKey)
                    .put("descriptorVersion", step.descriptorVersion).put("replaySafety", step.replaySafety)
                "ToolInvocationPrepared"
            }
            is AgentStep.ToolExecutionClaimed -> {
                payload.put("invocationId", step.invocationId.value).put("toolCallId", step.toolCallId)
                    .put("toolId", step.toolId).put("idempotencyKey", step.idempotencyKey)
                    .put("replaySafety", step.replaySafety)
                "ToolExecutionClaimed"
            }
            is AgentStep.ResourceBlocked -> {
                val rIds = JSONArray().apply { step.resourceIds.forEach { put(it) } }
                val eIds = JSONArray().apply { step.endpointIds.forEach { put(it) } }
                val bIds = JSONArray().apply { step.blockingLeaseIds.forEach { put(it) } }
                payload.put("invocationId", step.invocationId.value).put("toolId", step.toolId)
                    .put("resourceIds", rIds).put("endpointIds", eIds).put("blockingLeaseIds", bIds)
                "ResourceBlocked"
            }
            is AgentStep.ToolOutcomeRecorded -> {
                payload.put("invocationId", step.invocationId.value).put("toolCallId", step.toolCallId)
                    .put("toolId", step.toolId).put("success", step.success).put("output", step.output)
                    .put("actionTruth", step.actionTruth.name).put("failureKind", step.failureKind ?: "")
                "ToolOutcomeRecorded"
            }
            is AgentStep.InputRequested -> {
                val input = when (val req = step.input) {
                    is AgentInputRequest.Approval -> JSONObject().put("inputType", "APPROVAL")
                        .put("invocationId", req.invocationId.value).put("toolId", req.toolId.value)
                        .put("action", req.action).put("risk", req.risk)
                        .put("argumentsDigest", req.argumentsDigest)
                        .put("detail", req.detail ?: "")
                        .put("speechPrompt", req.speechPrompt ?: "")
                        .put("expiresAt", req.expiresAt?.toString() ?: "")
                    is AgentInputRequest.Clarification -> JSONObject().put("inputType", "CLARIFICATION")
                        .put("question", req.question).put("field", req.field)
                        .put("speechPrompt", req.speechPrompt ?: "")
                        .put("expiresAt", req.expiresAt?.toString() ?: "")
                        .put("expected", encodeExpected(req.expected))
                }
                payload.put("requestId", step.requestId.value).put("input", input)
                "InputRequested"
            }
            is AgentStep.InputResolved -> {
                payload.put("requestId", step.requestId.value).put("resolution", step.resolution)
                "InputResolved"
            }
            is AgentStep.ContinuationPrepared -> {
                payload.put("turnId", step.turnId.value).put("messageCount", step.messageCount)
                    .put("exposedToolIds", JSONArray(step.exposedToolIds.toList()))
                "ContinuationPrepared"
            }
            is AgentStep.ResponseCommitted -> {
                payload.put("commitId", step.commitId).put("interactionId", step.interactionId)
                "ResponseCommitted"
            }
            is AgentStep.RecoveryClaimed -> {
                payload.put("planKind", step.planKind).put("evidenceStepId", step.evidenceStepId ?: "")
                "RecoveryClaimed"
            }
        }
        return payload to type
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
                SpeechOutputTarget.Device(it, null, metadata?.optString("outputName", "Wearable") ?: "Wearable", AudioDeviceKind.WEARABLE)
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

            "RunPrepared" -> AgentStep.RunPrepared(step, run, at,
                runCatching { AgentRunKind.valueOf(p.getString("kind")) }.getOrDefault(AgentRunKind.INTERACTIVE),
                runCatching { RunRecoveryPolicy.valueOf(p.getString("recoveryPolicy")) }.getOrDefault(RunRecoveryPolicy.USER_RESUME))
            "ProviderTurnStarted" -> AgentStep.ProviderTurnStarted(step, run, at,
                ProviderTurnId(p.getString("turnId")), p.getString("providerId"), p.optString("modelId").ifBlank { null },
                p.optString("requestIdentity"), stringSet(p, "exposedToolIds"),
                stringSet(p, "requiredCapabilities"), stringSet(p, "contextRequirements"))
            "ProviderTurnCompleted" -> {
                val calls = mutableListOf<ProviderToolCallRecord>()
                val array = p.optJSONArray("toolCalls") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    calls += ProviderToolCallRecord(item.getString("toolCallId"), item.getString("toolName"),
                        item.optString("argumentsJson", "{}"))
                }
                AgentStep.ProviderTurnCompleted(step, run, at, ProviderTurnId(p.getString("turnId")),
                    p.optString("assistantText"), calls, p.optString("finishReason", "unknown"))
            }
            "ToolInvocationPrepared" -> AgentStep.ToolInvocationPrepared(step, run, at,
                ToolInvocationId(p.getString("invocationId")), call, tool, p.optString("argumentsJson", "{}"),
                p.optString("argumentsDigest"), p.optString("idempotencyKey", id),
                p.optInt("descriptorVersion", 0), p.optString("replaySafety", "NEVER_REPLAY"))
            "ToolExecutionClaimed" -> AgentStep.ToolExecutionClaimed(step, run, at,
                ToolInvocationId(p.getString("invocationId")), call, tool,
                p.optString("idempotencyKey", idempotencyKey ?: id), p.optString("replaySafety", "NEVER_REPLAY"))
            "ResourceBlocked" -> {
                val rArray = p.optJSONArray("resourceIds")
                val eArray = p.optJSONArray("endpointIds")
                val bArray = p.optJSONArray("blockingLeaseIds")
                val rList = buildList { if (rArray != null) for (i in 0 until rArray.length()) add(rArray.getString(i)) }
                val eList = buildList { if (eArray != null) for (i in 0 until eArray.length()) add(eArray.getString(i)) }
                val bList = buildList { if (bArray != null) for (i in 0 until bArray.length()) add(bArray.getString(i)) }
                AgentStep.ResourceBlocked(step, run, at, ToolInvocationId(p.optString("invocationId")), tool, rList, eList, bList)
            }
            "ToolOutcomeRecorded" -> AgentStep.ToolOutcomeRecorded(step, run, at,
                ToolInvocationId(p.getString("invocationId")), call, tool, p.getBoolean("success"),
                p.optString("output"), runCatching { ActionTruth.valueOf(p.getString("actionTruth")) }.getOrDefault(ActionTruth.UNKNOWN),
                p.optString("failureKind").ifBlank { null })
            "ToolRejected" -> AgentStep.ToolRejected(step, run, at, call, tool,
                runCatching { ToolRejectionCode.valueOf(p.optString("reasonCode")) }
                    .getOrDefault(ToolRejectionCode.AUTHORIZATION_DENIED),
                p.optString("reason"), p.optBoolean("retryable", false),
                p.optBoolean("requiresUserAction", false), p.optString("modelResult"))
            "InputRequested" -> {
                val input = p.getJSONObject("input")
                val requestId = AgentInputRequestId(p.getString("requestId"))
                val expiresAt = input.optString("expiresAt").takeIf(String::isNotBlank)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                when (input.optString("inputType")) {
                    "APPROVAL" -> AgentStep.InputRequested(step, run, at, requestId, AgentInputRequest.Approval(
                        id = requestId, runId = run, createdAt = at, expiresAt = expiresAt,
                        invocationId = ToolInvocationId(input.getString("invocationId")),
                        toolId = ToolId(input.getString("toolId")),
                        action = input.getString("action"),
                        detail = input.optString("detail").ifBlank { null },
                        risk = input.optString("risk", "SENSITIVE"),
                        argumentsDigest = input.getString("argumentsDigest"),
                        speechPrompt = input.optString("speechPrompt").ifBlank { null }))
                    else -> AgentStep.InputRequested(step, run, at, requestId, AgentInputRequest.Clarification(
                        id = requestId, runId = run, createdAt = at, expiresAt = expiresAt,
                        question = input.optString("question"), field = input.optString("field"),
                        expected = decodeExpected(input.optJSONObject("expected")),
                        speechPrompt = input.optString("speechPrompt").ifBlank { null }))
                }
            }
            "InputResolved" -> AgentStep.InputResolved(step, run, at,
                AgentInputRequestId(p.getString("requestId")), p.getString("resolution"))
            "ContinuationPrepared" -> AgentStep.ContinuationPrepared(step, run, at,
                ProviderTurnId(p.getString("turnId")), p.optInt("messageCount"), stringSet(p, "exposedToolIds"))
            "ResponseCommitted" -> AgentStep.ResponseCommitted(step, run, at,
                p.getString("commitId"), p.getString("interactionId"))
            "RecoveryClaimed" -> AgentStep.RecoveryClaimed(
                step, run, at, p.optString("planKind"), p.optString("evidenceStepId").ifBlank { null })
            // Legacy reasoning is deliberately not exposed as public execution evidence.
            else -> AgentStep.ToolFailed(step, run, at, id, "legacy", "Unsupported historical step: $type")
        }
    }

    private fun encodeExpected(expected: ExpectedInput): JSONObject {
        val obj = JSONObject()
        return when (expected) {
            is ExpectedInput.Confirmation -> obj.put("kind", "CONFIRMATION")
            is ExpectedInput.Text -> obj.put("kind", "TEXT")
                .put("placeholder", expected.placeholder ?: "")
                .put("minLength", expected.minLength).put("maxLength", expected.maxLength)
                .put("singleLine", expected.singleLine).put("sensitive", expected.sensitive)
            is ExpectedInput.Choice -> {
                obj.put("kind", "CHOICE").put("allowMultiple", expected.allowMultiple)
                val options = JSONArray()
                expected.options.forEach { option ->
                    options.put(JSONObject().put("id", option.id).put("label", option.label)
                        .put("description", option.description ?: ""))
                }
                obj.put("options", options)
            }
            is ExpectedInput.Temporal -> obj.put("kind", "TEMPORAL").put("mode", expected.mode.name)
            is ExpectedInput.Files -> {
                obj.put("kind", "FILES").put("allowMultiple", expected.allowMultiple)
                obj.put("acceptedKinds", JSONArray(expected.acceptedKinds.map { it.name }))
            }
        }
    }

    private fun decodeExpected(obj: JSONObject?): ExpectedInput {
        if (obj == null) return ExpectedInput.Text()
        return when (obj.optString("kind")) {
            "CONFIRMATION" -> ExpectedInput.Confirmation
            "CHOICE" -> {
                val options = mutableListOf<ExpectedChoice>()
                val array = obj.optJSONArray("options") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    options += ExpectedChoice(item.getString("id"), item.getString("label"),
                        item.optString("description").ifBlank { null })
                }
                ExpectedInput.Choice(options, obj.optBoolean("allowMultiple", false))
            }
            "TEMPORAL" -> ExpectedInput.Temporal(
                runCatching { TemporalInputMode.valueOf(obj.optString("mode", "DATE_TIME")) }
                    .getOrDefault(TemporalInputMode.DATE_TIME))
            "FILES" -> {
                val kinds = mutableListOf<FileKind>()
                val array = obj.optJSONArray("acceptedKinds") ?: JSONArray()
                for (i in 0 until array.length()) {
                    runCatching { FileKind.valueOf(array.getString(i)) }.getOrNull()?.let { kinds += it }
                }
                ExpectedInput.Files(if (kinds.isEmpty()) setOf(FileKind.ANY) else kinds.toSet(),
                    obj.optBoolean("allowMultiple", false))
            }
            // TEXT (and legacy missing expected): initial values are never persisted.
            else -> ExpectedInput.Text(
                placeholder = obj.optString("placeholder").ifBlank { null },
                initialValue = null,
                minLength = obj.optInt("minLength", 0),
                maxLength = obj.optInt("maxLength", 2000),
                singleLine = obj.optBoolean("singleLine", false),
                sensitive = obj.optBoolean("sensitive", false),
            )
        }
    }

    private fun stringSet(p: JSONObject, key: String): Set<String> {
        val array = p.optJSONArray(key) ?: return emptySet()
        return buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
    }

    private companion object {
        const val STEP_SCHEMA_VERSION = 5
    }
}
