package com.halo.app.di.tools.host

import com.halo.app.privacy.HaloPauseLength
import com.halo.app.privacy.HaloPauseOwner
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal class HaloPauseTools(private val owner: HaloPauseOwner, private val authorize: () -> Unit) : ToolExecutor {
    override fun definitions() = listOf(
        definition(STATUS, "Read Halo's exact global pause state and version.", emptyMap()),
        definition(START, "After asking the user, pause Halo for the chosen amount of time. The paused assistant cannot respond until the user resumes from the phone.",
            mapOf("version" to schemaString, "length" to mapOf("type" to "string", "enum" to HaloPauseLength.entries.map { it.name }))),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = try {
        authorize()
        val args = toolCall.arguments
        val result = when (toolCall.functionName) {
            STATUS -> { require(args.isEmpty()); owner.state.value }
            START -> {
                require(args.keys == setOf("version", "length"))
                owner.pause(args.getValue("version") as String, HaloPauseLength.valueOf(args.getValue("length") as String), authorize)
            }
            else -> error("Unknown pause tool")
        }
        ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("version", result.version)
            .put("paused", result.paused).put("untilMs", result.untilMillis).toString())
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
          "Pause was unavailable or changed. Read the current state before continuing.", ToolRejectionKind.INVALID_ARGUMENTS) }

    companion object {
        const val STATUS = "halo_pause_status"
        const val START = "halo_pause_start"
        private val schemaString = mapOf("type" to "string", "minLength" to 1, "maxLength" to 40)
        private fun definition(name: String, description: String, args: Map<String, Any>) =
            ToolDefinition(name, name, description,
                mapOf("type" to "object", "properties" to args, "required" to args.keys.toList(), "additionalProperties" to false))

        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in setOf(STATUS, START)) return null
            val change = definition.name == START
            return ToolDescriptor(ToolId(definition.name), definition, ToolCategory.HOST_CONTROL,
                keywords = setOf("pause", "quiet", "privacy"),
                security = if (change) ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY)
                    else ToolSecurityPolicy.READ_ONLY,
                operability = ToolOperability(capabilityId = CapabilityId("halo.pause"), owner = "HaloPauseOwner",
                    operation = if (change) ToolOperation.EDIT else ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
                    resultSchema = mapOf("type" to "object", "properties" to mapOf("version" to schemaString,
                        "paused" to mapOf("type" to "boolean"), "untilMs" to mapOf("type" to "integer")),
                        "required" to listOf("version", "paused", "untilMs"), "additionalProperties" to false),
                    sourceVersionArguments = if (change) setOf("version") else emptySet(), resultIdFields = setOf("version"),
                    limitations = setOf("Unlocked adult phone only. Resume on the phone because Halo blocks assistant requests while paused.")))
        }
    }
}
