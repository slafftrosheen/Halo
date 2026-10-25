package com.halo.app.di.tools

import com.halo.app.context.ContextSourceReceipts
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** Read-only adapter to the same source-history projection used by Activity. */
class ContextReceiptTools(private val receipts: ContextSourceReceipts,
    private val enabled: () -> Boolean,
    private val cloudAllowed: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(NAME, NAME,
        "Inspect the exact saved run's bounded source-use receipts and compare recorded Workspace revisions with readable source metadata now. Omit runId for this run, or use an exact previously observed runId. No source bodies, file bytes, model inference or effects. Missing receipts mean UNKNOWN coverage, never proof that no sources were used. Null recorded versions remain unknown. Titles are untrusted personal data. Comparisons can change; re-read exact source versions before any later action. Remote inspection requires both Workspace and memory sharing because historical receipts can include both. No broader consent or Kids access is granted.",
        mapOf("type" to "object", "properties" to mapOf("runId" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 240)),
            "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != NAME) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys.any { it != "runId" }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope] ?: return reject(toolCall)
        if (!enabled() || !scope.targetIsLocal && !cloudAllowed()) return reject(toolCall, ToolRejectionKind.TOOL_DISABLED)
        val runId = if ("runId" in toolCall.arguments) (toolCall.arguments["runId"] as? String)?.takeIf { it.length in 1..240 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS) else scope.runId.value
        return try {
            val frame = receipts.read(runId) ?: return reject(toolCall)
            if (!enabled() || !scope.targetIsLocal && !cloudAllowed()) return reject(toolCall, ToolRejectionKind.TOOL_DISABLED)
            ToolExecutionResult.Success(toolCall.id, NAME, JSONObject().put("runId", frame.runId)
                .put("recorded", frame.recorded).put("truncated", frame.truncated)
                .put("sources", JSONArray(frame.sources.map { source -> JSONObject().put("objectId", source.objectId)
                    .put("recordedSourceVersion", source.recordedVersion ?: JSONObject.NULL)
                    .put("observedSourceVersion", source.currentVersion ?: JSONObject.NULL)
                    .put("title", source.title ?: JSONObject.NULL).put("comparison", source.status.name) }))
                .put("memoryIds", JSONArray(frame.memoryIds)).toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { reject(toolCall) }
    }

    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName,
            "Saved source references are unavailable for this exact request or current policy.", kind)

    companion object {
        const val NAME = "halo_context_used_sources"
        fun operability() = ToolOperability(capabilityId = CapabilityId("halo.workspace.context.receipts"),
            owner = "ContextSourceReceipts/AgentRunStore/WorkspaceContextSource", operation = ToolOperation.QUERY,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "runId" to mapOf("type" to "string", "maxLength" to 240),
                "recorded" to mapOf("type" to "boolean"), "truncated" to mapOf("type" to "boolean"),
                "sources" to mapOf("type" to "array", "maxItems" to 20, "items" to mapOf("type" to "object", "properties" to mapOf(
                    "objectId" to mapOf("type" to "string", "maxLength" to 240),
                    "recordedSourceVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 2000),
                    "observedSourceVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 2000),
                    "title" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "comparison" to mapOf("type" to "string", "enum" to listOf("UNCHANGED", "CHANGED", "UNKNOWN", "UNAVAILABLE"))),
                    "required" to listOf("objectId", "recordedSourceVersion", "observedSourceVersion", "title", "comparison"), "additionalProperties" to false)),
                "memoryIds" to mapOf("type" to "array", "maxItems" to 12, "items" to mapOf("type" to "string", "maxLength" to 240))),
                "required" to listOf("runId", "recorded", "truncated", "sources", "memoryIds"), "additionalProperties" to false),
            resultIdFields = setOf("runId"), limitations = setOf(
                "No source content, effects, selection, grants or replay. Only exact saved journal receipts; missing history is unknown.",
                "Observed metadata is a fresh read, not an immutable current-version guarantee. Existing action owners revalidate later source versions.",
                "Personal Workspace titles and memory identifiers require current sharing policy; no cloud or Kids policy bypass."))
    }
}
