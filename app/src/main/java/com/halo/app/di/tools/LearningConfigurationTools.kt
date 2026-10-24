package com.halo.app.di.tools

import com.halo.app.learning.LearningConfigurationOwner
import com.halo.app.learning.LearningConfigurationSnapshot
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.memory.LearningPreference
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LearningConfigurationTools(private val owner: LearningConfigurationOwner,
    private val canConfigure: () -> Boolean = { false }) : ToolExecutor {
    companion object {
        val names = setOf("halo_get_learning_configuration", "halo_set_learning_configuration")
        private val booleans = setOf("suggestMemories", "suggestProcedures", "suggestAutomations", "modelReflection")
        private val properties = mapOf("sourceVersion" to mapOf("type" to "string", "pattern" to "^learning:[a-f0-9]{64}$"),
            "mode" to mapOf("type" to "string", "enum" to LearningPreference.entries.map { it.name })) +
            booleans.associateWith { mapOf("type" to "boolean") }
        fun operability(name: String): ToolOperability? = if (name !in names) null else ToolOperability(
            capabilityId = CapabilityId("host.assistant.learning"), owner = "LearningConfigurationOwner",
            operation = if (name == "halo_get_learning_configuration") ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to properties,
                "required" to properties.keys.toList(), "additionalProperties" to false),
            sourceVersionArguments = if (name == "halo_set_learning_configuration") setOf("sourceVersion") else emptySet(),
            limitations = setOf("Configuration only; no memory, procedure, automation or reflection is created or approved.",
                "Suggestion options require SUGGEST mode. Local reflection remains locally eligible and existing approval/budget policy applies.",
                "Memory-use/sharing consent remains with the existing mode-profile controls. No playback, network or OS permission change."))
    }
    override fun definitions() = names.map { name -> ToolDefinition(name, name,
        if (name == "halo_get_learning_configuration") "Read current local learning/suggestion configuration and exact sourceVersion. No memory contents or reflection request."
        else "After approval change explicitly requested local learning/suggestion preferences at exact sourceVersion. Options are editable only in SUGGEST mode. Never creates memories/automations or approves suggestions, enables remote disclosure or runs reflection. Never replay uncertain writes.",
        mapOf("type" to "object", "properties" to if (name == "halo_set_learning_configuration") properties else emptyMap<String, Any>(),
            "required" to if (name == "halo_set_learning_configuration") listOf("sourceVersion") else emptyList<String>(), "additionalProperties" to false)) }
    private fun result(snapshot: LearningConfigurationSnapshot): String = snapshot.preferences.let { p -> JSONObject()
        .put("sourceVersion", snapshot.sourceVersion).put("mode", p.mode.name).put("suggestMemories", p.suggestMemories)
        .put("suggestProcedures", p.suggestProcedures).put("suggestAutomations", p.suggestAutomations).put("modelReflection", p.modelReflection).toString() }
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun reject(message: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        var writing = false
        try {
            if (toolCall.functionName !in names) return@withContext reject("Unknown learning operation", ToolRejectionKind.TOOL_UNKNOWN)
            val args = toolCall.arguments
            val old = owner.snapshot()
            if (toolCall.functionName == "halo_get_learning_configuration") {
                if (args.isNotEmpty()) return@withContext reject("Read accepts no arguments")
                return@withContext ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result(old), ActionTruth.ACKNOWLEDGED)
            }
            if (context.interactionId.isNullOrBlank() || !canConfigure()) return@withContext reject("Open a grown-up space and approve this change", ToolRejectionKind.PERMISSION_REQUIRED)
            if (args.size < 2 || args.keys.any { it !in properties }) return@withContext reject("Supply only explicit supported settings and sourceVersion")
            if (args["sourceVersion"] != old.sourceVersion) return@withContext reject("Settings changed. Read them again.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            val mode = if ("mode" in args) LearningPreference.entries.singleOrNull { it.name == args["mode"] }
                ?: return@withContext reject("Unknown learning mode") else old.preferences.mode
            if (args.keys.any { it in booleans } && mode != LearningPreference.SUGGEST) return@withContext reject("Suggestion options require SUGGEST mode")
            fun option(key: String, previous: Boolean) = if (key !in args) previous else args[key] as? Boolean ?: throw IllegalArgumentException()
            val next = old.preferences.copy(mode = mode, suggestMemories = option("suggestMemories", old.preferences.suggestMemories),
                suggestProcedures = option("suggestProcedures", old.preferences.suggestProcedures),
                suggestAutomations = option("suggestAutomations", old.preferences.suggestAutomations), modelReflection = option("modelReflection", old.preferences.modelReflection))
            writing = true
            val saved = owner.apply(old.sourceVersion, next)
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result(saved), ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { reject("Invalid learning configuration") }
        catch (_: Exception) {
            if (writing) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Settings outcome uncertain; read current settings before retrying",
                failureKind = ToolFailureKind.RESOURCE_UNAVAILABLE, completionUnknown = true)
            else reject("Learning settings unavailable", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
}
