package com.halo.app.di.tools.host

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.toolkit.HaloAlbumFindRequests
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class AlbumFindTools(private val foregroundUnlocked: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(
        FIND, FIND, "Open Album and apply the user's words to its on-device photo search. Only an opaque UI dispatch receipt is returned. See matches in Album.",
        mapOf("type" to "object", "additionalProperties" to false, "required" to listOf("phrase"),
            "properties" to mapOf("phrase" to mapOf("type" to "string", "minLength" to 1, "maxLength" to HaloAlbumFindRequests.MAX_PHRASE))),
    ))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.functionName != FIND) return reject("Unknown Album action.", ToolRejectionKind.TOOL_UNKNOWN)
        val phrase = (toolCall.arguments["phrase"] as? String)?.takeIf {
            it.length <= HaloAlbumFindRequests.MAX_PHRASE && it.isNotBlank() && it.none(Char::isISOControl)
        }?.trim()
        if (toolCall.arguments.keys != setOf("phrase") || phrase == null)
            return reject("Supply one short photo search phrase.", ToolRejectionKind.INVALID_ARGUMENTS)
        return withContext(Dispatchers.Main.immediate) {
            val revision = runCatching { Graph.kidsPreferences.adultActionRevision() }.getOrNull()
            if (revision == null || !foregroundUnlocked())
                return@withContext reject("Open Halo on the unlocked adult phone.", ToolRejectionKind.PERMISSION_REQUIRED)
            val requestId = HaloAlbumFindRequests.dispatch(phrase, revision, Graph.requestedHaloScreen)
                ?: return@withContext reject("Another page request is already pending.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            ToolExecutionResult.Success(toolCall.id, FIND, JSONObject().put("requestId", requestId).toString(), ActionTruth.DISPATCHED)
        }
    }

    companion object {
        const val FIND = "halo_album_find"
        fun descriptor(definition: ToolDefinition): ToolDescriptor? = if (definition.name != FIND) null else ToolDescriptor(
            id = ToolId(FIND), definition = definition, category = ToolCategory.HOST_CONTROL,
            keywords = setOf("album", "photo", "picture", "gallery", "find"),
            security = ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY, ReplaySafety.NEVER_REPLAY),
            operability = ToolOperability(capabilityId = CapabilityId("halo.album"), owner = "HaloAlbumScreen", operation = ToolOperation.PRESENT,
                resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "additionalProperties" to false,
                    "properties" to mapOf("requestId" to mapOf("type" to "string")), "required" to listOf("requestId")),
                resultIdFields = setOf("requestId"), limitations = setOf("Foreground unlocked adult phone only. Results stay in the local Album UI.")),
        )
    }
}
