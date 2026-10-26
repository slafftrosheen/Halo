package com.halo.app.di.tools

import com.halo.app.ai.LocalRoleControl
import com.halo.app.ai.LocalRoleSnapshot
import com.halo.sdk.provider.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class LocalModelRoleTools(private val owner: LocalRoleControl,
    private val canConfigure: () -> Boolean = { false }) : ToolExecutor {
    private val role = mapOf("type" to "string", "enum" to AiModelRole.entries.filter { it != AiModelRole.CHAT }.map { it.name })
    private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String>) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields,
            "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_local_role_state", "Read the actual non-CHAT model-role binding and eligible installed local candidates with sourceVersion. No generation, download, test, network, credentials or measured qualification. Default CHAT/provider/model/routing stays with existing mode-profile tools.",
            mapOf("role" to role, "offset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 10000)), listOf("role")),
        definition("halo_local_role_select", "After durable approval update the exact observed native role through the existing router: explicit eligible installed local target, disable, or inherit an eligible local default. INHERIT requires LOCAL_ONLY or an already-local EXPLICIT routing policy. An explicit local role may coexist with a remote conversation default; the default is unchanged. No AUTOMATIC, remote target/provider changes, profile activation, consent, download, generation or qualification. Never replay uncertain selection; read again.",
            mapOf("role" to role, "sourceVersion" to mapOf("type" to "string", "pattern" to "^local-role:[a-f0-9]{64}$"),
                "selection" to mapOf("type" to "string", "enum" to listOf("EXPLICIT", "INHERIT_DEFAULT", "DISABLED")),
                "providerId" to id, "modelId" to id), listOf("role", "sourceVersion", "selection")))
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id,
            toolCall.functionName, "Role selection unavailable, changed or not authorized. Read current role state; remote/default profile changes stay in Mode profiles.", kind)
        var requested = false
        try {
            val definition = definitions().singleOrNull { it.name == toolCall.functionName } ?: return reject(ToolRejectionKind.TOOL_UNKNOWN)
            val args = toolCall.arguments
            val fields = definition.parameters["properties"] as Map<*, *>
            val required = definition.parameters["required"] as List<*>
            require(args.keys.all { it in fields } && args.keys.containsAll(required.filterIsInstance<String>()))
            if (!canConfigure()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            val role = AiModelRole.entries.firstOrNull { it.name == args["role"] && it != AiModelRole.CHAT } ?: return reject()
            val reading = toolCall.functionName == "halo_local_role_state"
            val offset = if ("offset" in args) (args["offset"] as? Number)?.let {
                require(it.toDouble().isFinite() && it.toDouble() == it.toLong().toDouble() && it.toLong() in 0..10000); it.toInt()
            } ?: return reject() else 0
            val value = if (reading) owner.snapshot(role) else {
                if (context.interactionId.isNullOrBlank()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
                val selection = AiRoleSelection.entries.firstOrNull { it.name == args["selection"] && it != AiRoleSelection.AUTOMATIC } ?: return reject()
                val source = args["sourceVersion"] as? String ?: return reject()
                require(source.matches(Regex("local-role:[a-f0-9]{64}")))
                fun id(key: String) = (args[key] as? String)?.also { require(it.length in 1..200 && it.none(Char::isISOControl)) } ?: throw IllegalArgumentException()
                val target = if (selection == AiRoleSelection.EXPLICIT) AiModelRef(id("providerId"), id("modelId")) else {
                    require("providerId" !in args && "modelId" !in args); null
                }
                requested = true
                owner.select(role, source, selection, target, canConfigure)
            } ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (!canConfigure()) return if (requested) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Role access changed after dispatch. Completion metadata withheld; never replay this selection.", completionUnknown = true)
                else reject(ToolRejectionKind.PERMISSION_REQUIRED)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result(value, offset).toString(),
                if (reading) ActionTruth.ACKNOWLEDGED else ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return if (requested) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Role write completion is unknown. Read current binding when authorized; never replay this selection.", completionUnknown = true) else reject() }
        catch (_: Exception) { return if (requested) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Role write completion is unknown. Read current binding; never replay this selection.", completionUnknown = true)
            else reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    private fun result(value: LocalRoleSnapshot, offset: Int) = JSONObject().put("role", value.role.name)
        .put("sourceVersion", value.sourceVersion).put("selection", value.binding.selection.name)
        .put("providerId", value.binding.providerId ?: JSONObject.NULL).put("modelId", value.binding.modelId ?: JSONObject.NULL)
        .put("localPolicy", value.localPolicy).put("candidates", JSONArray(value.candidates.drop(offset).take(30).map {
            JSONObject().put("providerId", it.providerId).put("modelId", it.modelId)
        })).put("nextOffset", if (offset + 30 < value.candidates.size) offset + 30 else JSONObject.NULL)
        .put("generationPerformed", false).put("qualificationMeasured", false).put("profileChanged", false)
}
