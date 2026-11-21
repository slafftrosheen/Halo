package com.halo.app.di.tools.host

import com.halo.app.continuity.FollowMePreferences
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Settings only. User-approved delivery and response checks stay with the live companion. */
internal class FollowMeTools(private val owner: FollowMePreferences, private val authorize: () -> Unit) : ToolExecutor {
    override fun definitions() = listOf(
        definition(STATUS, "Read the current Follow Me choices and their version.", emptyMap()),
        definition(SET, "After approval, change the current Follow Me and safe Watch approval choices. This does not pair a device or send a reply.",
            mapOf("version" to stringType, "enabled" to booleanType, "watchApprovals" to booleanType)),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = try {
        authorize()
        val prior = owner.state.value
        val args = toolCall.arguments
        val updated = when (toolCall.functionName) {
            STATUS -> { require(args.isEmpty()); prior }
            SET -> {
                require(args.keys == setOf("version", "enabled", "watchApprovals") && args["version"] == prior.version)
                owner.update(prior, prior.copy(enabled = args.getValue("enabled") as Boolean,
                    watchApprovals = args.getValue("watchApprovals") as Boolean), authorize)
            }
            else -> error("Unknown Follow Me tool")
        }
        ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
            JSONObject().put("version", updated.version).put("enabled", updated.enabled)
                .put("watchApprovals", updated.watchApprovals).toString())
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
          "Follow Me access or choices changed. Read the current settings first.", ToolRejectionKind.INVALID_ARGUMENTS) }

    companion object {
        const val STATUS = "halo_follow_me_status"
        const val SET = "halo_follow_me_configure"
        private val stringType = mapOf("type" to "string", "minLength" to 1, "maxLength" to 40)
        private val booleanType = mapOf("type" to "boolean")
        private fun fields(properties: Map<String, Any>) = mapOf("type" to "object", "properties" to properties,
            "required" to properties.keys.toList(), "additionalProperties" to false)
        private fun definition(name: String, description: String, input: Map<String, Any>) =
            ToolDefinition(name, name, description, fields(input))

        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in setOf(STATUS, SET)) return null
            val change = definition.name == SET
            return ToolDescriptor(ToolId(definition.name), definition, ToolCategory.HOST_CONTROL,
                keywords = setOf("follow", "watch", "glasses", "devices"),
                security = if (change) ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY)
                    else ToolSecurityPolicy.READ_ONLY,
                operability = ToolOperability(capabilityId = CapabilityId("halo.follow_me"), owner = "FollowMePreferences",
                    operation = if (change) ToolOperation.EDIT else ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
                    sourceVersionArguments = if (change) setOf("version") else emptySet(),
                    resultIdFields = setOf("version"),
                    resultSchema = fields(mapOf("version" to stringType, "enabled" to booleanType, "watchApprovals" to booleanType)),
                    limitations = setOf("Needs adult foreground Halo. It does not pair a device or authorize an outgoing reply.")))
        }
    }
}
