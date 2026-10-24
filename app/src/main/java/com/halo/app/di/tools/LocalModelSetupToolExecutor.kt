package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONObject

/** Requests the existing installer; never selects/qualifies models or owns downloads itself. */
class LocalModelSetupToolExecutor(private val owner: LocalRolePackControl) : ToolExecutor {
    private val commandNames = mapOf("halo_local_setup_start" to LocalRolePackCommand.START_OR_RESUME,
        "halo_local_setup_pause" to LocalRolePackCommand.PAUSE, "halo_local_setup_rollback" to LocalRolePackCommand.ROLLBACK,
        "halo_local_setup_keep_files" to LocalRolePackCommand.KEEP_FILES)
    override fun definitions() = listOf(
        ToolDefinition("halo_local_setup_status", "halo_local_setup_status", "Read pinned local starter-bundle availability and current acquisition transaction. Files installed does not mean selected, tested, recommended or audible. No user documents or provider call.",
            mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)),
    ) + commandNames.map { (name, command) -> ToolDefinition(name, name, when (command) {
        LocalRolePackCommand.START_OR_RESUME -> "Explicitly request/resume downloads of the pinned intent candidate, recognition, VAD and speech-output files. Can require substantial network/storage. Reuses existing installer; does not select candidates or claim language/device quality. Status first; supply its exact transactionId (NONE for fresh setup). Never replay an uncertain request."
        LocalRolePackCommand.PAUSE -> "Pause only the exact observed local starter setup and its own downloads. Keeps resumable files; never cancels unrelated acquisitions. No automatic resume."
        LocalRolePackCommand.ROLLBACK -> "Request removal of only artifacts proven owned by the exact setup transaction, with existing required approval. Keeps unrelated, pre-existing, changed, selected or in-use models. Legacy/unknown ownership needs explicit keep-files recovery. Dispatch is not completed removal."
        LocalRolePackCommand.KEEP_FILES -> "Explicitly keep all downloaded files/selections and clear only the exact setup receipt. This ends its automatic rollback option; no files are removed, no download resumes and no role is selected."
    }, mapOf("type" to "object", "properties" to mapOf("transactionId" to mapOf("type" to "string",
        "pattern" to "^(NONE|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$")),
        "required" to listOf("transactionId"), "additionalProperties" to false)) }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName == "halo_local_setup_status") {
            if (toolCall.arguments.isNotEmpty()) return rejected(toolCall)
            val value = owner.state.value
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("available", owner.available)
                .put("transactionId", value.transactionId ?: "NONE").put("phase", value.phase.name).put("stage", value.stage.name)
                .put("busy", value.busy).put("canResume", value.canResume).put("canRollback", value.canRollback)
                .put("hasSetupRecord", value.hasSetupRecord).put("recoveryRequired", value.recoveryRequired)
                .put("canKeepFiles", value.canKeepFiles)
                .put("downloadedBytes", value.downloadedBytes).put("totalBytes", value.totalBytes)
                .put("roleSelectionChanged", false).put("qualificationGranted", false).toString())
        }
        val command = commandNames[toolCall.functionName] ?: return rejected(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys != setOf("transactionId")) return rejected(toolCall)
        val token = toolCall.arguments["transactionId"] as? String ?: return rejected(toolCall)
        if (token != "NONE" && runCatching { java.util.UUID.fromString(token).toString() == token }.getOrDefault(false) != true) return rejected(toolCall)
        if (command == LocalRolePackCommand.START_OR_RESUME && !owner.available) return rejected(toolCall, ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if (!owner.request(command, token)) return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
            JSONObject().put("transactionId", token).put("state", "DISPATCHED").put("action", command.name).toString(), ActionTruth.DISPATCHED)
    }
    private fun rejected(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(
        call.id, call.functionName, "Setup request unavailable or changed. Read setup status and review the current transaction before continuing.", kind)
}
