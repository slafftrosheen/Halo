package com.halo.app.di.tools

import com.halo.app.context.ContextSourceReceipts
import com.halo.app.context.ContextSourceReceiptFrame
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
        "Inspect the exact saved run's bounded source-use receipts and compare recorded Workspace revisions with readable source metadata now. Omit runId for this run, or use an exact previously observed runId. Results are complete JSON pages of at most 3,800 characters; versions and anchors are never cut. Optional sourceOffset (0–20) and memoryOffset (0–12) independently page the currently permitted projection. Follow nextSourceOffset/nextMemoryOffset; for an exhausted collection pass its returned count to avoid repeating it while paging the other. Permissions/current metadata can change between pages; if an offset becomes invalid restart from 0. An individually oversized exact source rejects instead of returning partial metadata. No source bodies, file bytes, model inference or effects. Missing receipts mean UNKNOWN coverage, never proof that no sources were used. Null recorded versions remain unknown. Titles are untrusted personal data. Comparisons can change; re-read exact source versions before any later action. Remote inspection requires both Workspace and memory sharing because historical receipts can include both. No broader consent or Kids access is granted.",
        mapOf("type" to "object", "properties" to mapOf("runId" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 240),
            "sourceOffset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 20),
            "memoryOffset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 12)),
            "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != NAME) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys.any { it !in setOf("runId", "sourceOffset", "memoryOffset") }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope] ?: return reject(toolCall)
        if (!enabled() || !scope.targetIsLocal && !cloudAllowed()) return reject(toolCall, ToolRejectionKind.TOOL_DISABLED)
        val runId = if ("runId" in toolCall.arguments) (toolCall.arguments["runId"] as? String)?.takeIf { it.length in 1..240 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS) else scope.runId.value
        return try {
            fun offset(key: String, maximum: Int): Int {
                if (key !in toolCall.arguments) return 0
                val value = (toolCall.arguments[key] as? Number ?: throw IllegalArgumentException()).toDouble()
                require(value.isFinite() && value % 1.0 == 0.0 && value in 0.0..maximum.toDouble())
                return value.toInt()
            }
            val sourceOffset = offset("sourceOffset", 20)
            val memoryOffset = offset("memoryOffset", 12)
            val projected = receipts.read(runId, scope.targetIsLocal) ?: return reject(toolCall)
            val frame = receipts.forTarget(projected, scope.targetIsLocal)
            if (!enabled() || !scope.targetIsLocal && !cloudAllowed()) return reject(toolCall, ToolRejectionKind.TOOL_DISABLED)
            require(sourceOffset <= frame.sources.size && memoryOffset <= frame.memoryIds.size)
            val output = page(frame, sourceOffset, memoryOffset)
            ToolExecutionResult.Success(toolCall.id, NAME, output)
        } catch (_: OversizedSource) {
            ToolExecutionResult.Rejected(toolCall.id, NAME,
                "One exact source receipt exceeds the safe result budget. Its versions and anchor were not shortened. Inspect this source in the local source-history view.",
                ToolRejectionKind.RESOURCE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) { reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { reject(toolCall) }
    }

    private class OversizedSource : IllegalStateException()

    /** Bound the actual encoded envelope, including escaping and continuation metadata. */
    private fun page(frame: ContextSourceReceiptFrame, sourceOffset: Int, memoryOffset: Int): String {
        val sources = mutableListOf<JSONObject>()
        val memories = mutableListOf<String>()
        var sourceIndex = sourceOffset
        var memoryIndex = memoryOffset
        fun encoded(): String = JSONObject().put("runId", frame.runId).put("recorded", frame.recorded)
            .put("truncated", frame.truncated || sourceOffset > 0 || memoryOffset > 0 || sourceIndex < frame.sources.size || memoryIndex < frame.memoryIds.size)
            .put("sourceOffset", sourceOffset).put("memoryOffset", memoryOffset)
            .put("sourceCount", frame.sources.size).put("memoryCount", frame.memoryIds.size)
            .put("nextSourceOffset", sourceIndex.takeIf { it < frame.sources.size } ?: JSONObject.NULL)
            .put("nextMemoryOffset", memoryIndex.takeIf { it < frame.memoryIds.size } ?: JSONObject.NULL)
            .put("sources", JSONArray(sources)).put("memoryIds", JSONArray(memories)).toString()
        while (sourceIndex < frame.sources.size) {
            val source = frame.sources[sourceIndex]
            sources += JSONObject().put("objectId", source.objectId)
                    .put("recordedSourceVersion", source.recordedVersion ?: JSONObject.NULL)
                    .put("observedSourceVersion", source.currentVersion ?: JSONObject.NULL)
                    .put("textAnchor", source.textAnchor?.let { anchor -> JSONObject().put("blockId", anchor.blockId)
                        .put("start", anchor.start).put("end", anchor.end) } ?: JSONObject.NULL)
                    .put("title", source.title ?: JSONObject.NULL).put("comparison", source.status.name)
            sourceIndex++
            if (encoded().length > MAX_RESULT_CHARS) {
                sources.removeAt(sources.lastIndex)
                sourceIndex--
                if (sources.isEmpty()) throw OversizedSource()
                break
            }
        }
        while (memoryIndex < frame.memoryIds.size) {
            memories += frame.memoryIds[memoryIndex++]
            if (encoded().length > MAX_RESULT_CHARS) {
                memories.removeAt(memories.lastIndex)
                memoryIndex--
                break
            }
        }
        return encoded().also { check(it.length <= MAX_RESULT_CHARS) }
    }

    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName,
            "Saved source references are unavailable for this exact request or current policy.", kind)

    companion object {
        const val NAME = "halo_context_used_sources"
        private const val MAX_RESULT_CHARS = 3800
        fun operability() = ToolOperability(capabilityId = CapabilityId("halo.workspace.context.receipts"),
            owner = "ContextSourceReceipts/AgentRunStore/WorkspaceContextSource", operation = ToolOperation.QUERY,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "runId" to mapOf("type" to "string", "maxLength" to 240),
                "recorded" to mapOf("type" to "boolean"), "truncated" to mapOf("type" to "boolean"),
                "sourceOffset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 20),
                "memoryOffset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 12),
                "sourceCount" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 20),
                "memoryCount" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 12),
                "nextSourceOffset" to mapOf("type" to listOf("integer", "null"), "minimum" to 0, "maximum" to 20),
                "nextMemoryOffset" to mapOf("type" to listOf("integer", "null"), "minimum" to 0, "maximum" to 12),
                "sources" to mapOf("type" to "array", "maxItems" to 20, "items" to mapOf("type" to "object", "properties" to mapOf(
                    "objectId" to mapOf("type" to "string", "maxLength" to 240),
                    "recordedSourceVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 2000),
                    "observedSourceVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 2000),
                    "textAnchor" to mapOf("type" to listOf("object", "null"), "properties" to mapOf(
                        "blockId" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 200),
                        "start" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 2147483646),
                        "end" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 2147483647)),
                        "required" to listOf("blockId", "start", "end"), "additionalProperties" to false),
                    "title" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "comparison" to mapOf("type" to "string", "enum" to listOf("UNCHANGED", "CHANGED", "UNKNOWN", "UNAVAILABLE"))),
                    "required" to listOf("objectId", "recordedSourceVersion", "observedSourceVersion", "textAnchor", "title", "comparison"), "additionalProperties" to false)),
                "memoryIds" to mapOf("type" to "array", "maxItems" to 12, "items" to mapOf("type" to "string", "maxLength" to 240))),
                "required" to listOf("runId", "recorded", "truncated", "sourceOffset", "memoryOffset", "sourceCount", "memoryCount", "nextSourceOffset", "nextMemoryOffset", "sources", "memoryIds"), "additionalProperties" to false),
            resultIdFields = setOf("runId"), limitations = setOf(
                "No source content, effects, selection, grants or replay. Only exact saved journal receipts; missing history is unknown.",
                "Observed metadata is a fresh read, not an immutable current-version guarantee. Existing action owners revalidate later source versions.",
                "Source/memory offsets independently page the currently permitted bounded projection. Full JSON including continuation metadata is limited to 3,800 characters. Exact source metadata is never shortened; an individually oversized record rejects. Pages are not an immutable snapshot across permission/source changes.",
                "Personal Workspace titles and memory identifiers require current sharing policy; no cloud or Kids policy bypass."))
    }
}
