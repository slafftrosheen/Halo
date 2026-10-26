package com.halo.app.di.tools.host

import com.halo.app.context.CurrentScreenContext
import com.halo.app.context.CurrentScreenSuite
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** Explicit metadata query under the existing Screen and Workspace disclosure policy. It cannot invoke a UI callback or read/attach a document. */
class CurrentScreenTools(private val context: CurrentScreenContext,
    private val remoteReadAllowed: () -> Boolean = { false },
    private val canRead: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(NAME, NAME,
        "Read Halo's currently resumed page metadata: route template, suite, active tool, exact visible object and up to 100 selected identifiers with selectionCount and selectionTruncated and canonical source version when supplied by its owner. No text, titles, file content, screen capture or automatic attachment. UI action IDs are presentation identifiers, not callable tools. Listed canonical tools remain subject to their own schema, source, policy and approval checks. Optional expectedSnapshot rejects a changed page/selection/version. Requires foreground grown-up access. Remote queries additionally require the existing acknowledged Power profile and both Screen and Workspace disclosure; this query grants none of them. Navigation metadata does not prove effects or rendered usability.",
        mapOf("type" to "object", "properties" to mapOf("expectedSnapshot" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)),
            "required" to emptyList<String>(), "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != NAME) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys.any { it != "expectedSnapshot" }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val expected = if ("expectedSnapshot" in toolCall.arguments) (toolCall.arguments["expectedSnapshot"] as? String)
            ?.takeIf { it.length in 1..100 } ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS) else null
        val scope = currentCoroutineContext()[RunToolScope] ?: return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        if ((!scope.targetIsLocal && !remoteReadAllowed()) || !canRead()) return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        val frame = context.read(expected) ?: return reject(toolCall)
        val data = frame.metadata
        val output = JSONObject().put("schemaVersion", frame.schemaVersion).put("snapshot", frame.snapshot)
            .put("routeTemplate", data.routeTemplate).put("parentSuite", data.parentSuite.name).put("activeTool", data.activeTool)
            .put("objectId", data.objectId ?: JSONObject.NULL).put("sourceVersion", data.sourceVersion ?: JSONObject.NULL)
            .put("selectionCount", data.selectionCount).put("selectionTruncated", data.selectionTruncated)
            .put("selectionIds", JSONArray(data.selectionIds)).put("uiActionIds", JSONArray(data.uiActionIds))
            .put("canonicalToolIds", JSONArray(data.canonicalToolIds)).put("contentRead", false).put("authorityGranted", false)
        if (!canRead() || (!scope.targetIsLocal && !remoteReadAllowed()) || context.read(frame.snapshot) == null) return reject(toolCall)
        return ToolExecutionResult.Success(toolCall.id, NAME, output.toString(), ActionTruth.ACKNOWLEDGED)
    }

    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName,
            "Current page metadata is unavailable, changed, or not permitted for this request. No source was read or action performed.", kind)

    companion object {
        const val NAME = "halo_current_screen"
        fun operability() = ToolOperability(version = 1, capabilityId = CapabilityId("host.assistant.current_screen"),
            owner = "CurrentScreenContext/current resumed page", operation = ToolOperation.QUERY,
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = setOf("expectedSnapshot"),
            resultIdFields = setOf("snapshot", "objectId"),
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(1)),
                "snapshot" to mapOf("type" to "string", "maxLength" to 100),
                "routeTemplate" to mapOf("type" to "string", "maxLength" to 300),
                "parentSuite" to mapOf("type" to "string", "enum" to CurrentScreenSuite.entries.map { it.name }),
                "activeTool" to mapOf("type" to "string", "maxLength" to 100),
                "objectId" to mapOf("type" to listOf("string", "null"), "maxLength" to 240),
                "sourceVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 2000),
                "selectionCount" to mapOf("type" to "integer", "minimum" to 0), "selectionTruncated" to mapOf("type" to "boolean"),
                "selectionIds" to strings(100, 240), "uiActionIds" to strings(64, 160), "canonicalToolIds" to strings(32, 100),
                "contentRead" to mapOf("type" to "boolean", "enum" to listOf(false)),
                "authorityGranted" to mapOf("type" to "boolean", "enum" to listOf(false))),
                "required" to listOf("schemaVersion", "snapshot", "routeTemplate", "parentSuite", "activeTool", "objectId", "sourceVersion",
                    "selectionCount", "selectionTruncated", "selectionIds", "uiActionIds", "canonicalToolIds", "contentRead", "authorityGranted"), "additionalProperties" to false),
            limitations = setOf("Foreground grown-up metadata only; remote results require existing acknowledged Power, Screen and Workspace disclosure at admission and return. No content read, context grant, UI execution or automatic upload.",
                "Null sourceVersion is unknown, not a usable revision. Snapshot covers visible metadata only; action owners must revalidate source and permission.",
                "UI action IDs are not semantic tools. Canonical tool IDs do not grant availability or prove text/spoken action parity."))

        private fun strings(count: Int, length: Int) = mapOf("type" to "array", "maxItems" to count,
            "items" to mapOf("type" to "string", "maxLength" to length))
    }
}
