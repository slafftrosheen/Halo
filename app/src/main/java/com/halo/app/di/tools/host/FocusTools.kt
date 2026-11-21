package com.halo.app.di.tools.host

import android.content.Context
import com.halo.app.ui.toolkit.HaloFocusOwner
import com.halo.app.ui.toolkit.HaloFocusSession
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal class FocusTools(context: Context, private val allowed: () -> Boolean) : ToolExecutor {
    private val owner = HaloFocusOwner.get(context)
    override fun definitions() = listOf(
        tool(STATUS, "Read the active halo focus timer and its exact session version.", emptyMap()),
        tool(START, "Start a timed halo focus session. The tool never changes Android notification sounds.",
            mapOf("minutes" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 120))),
        tool(END, "End the exact active halo focus session. Read its version first.",
            mapOf("version" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 40))),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        fun rejected(reason: String) = ToolExecutionResult.Rejected(toolCall.id, name, reason, ToolRejectionKind.INVALID_ARGUMENTS)
        if (!allowed()) return rejected("Focus requires the adult unlocked phone.")
        return try {
            val args = toolCall.arguments
            val receipt = when (name) {
                STATUS -> { require(args.isEmpty()); receipt(owner.current()) }
                START -> {
                    require(args.keys == setOf("minutes"))
                    val n = (args["minutes"] as Number).toDouble()
                    require(n in 1.0..120.0 && n % 1.0 == 0.0 && allowed())
                    receipt(owner.start(n.toInt(), quiet = false))
                }
                END -> {
                    require(args.keys == setOf("version"))
                    val version = args["version"] as String
                    require(allowed() && owner.end(version))
                    JSONObject().put("active", false).put("version", version).put("ended", true)
                }
                else -> return rejected("Unknown focus action.")
            }
            ToolExecutionResult.Success(toolCall.id, name, receipt.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { rejected("Focus did not change. Read its current state and check the requested duration or version.") }
    }

    private fun receipt(session: HaloFocusSession?): JSONObject = JSONObject().put("active", session != null).apply {
        session?.let {
            put("version", it.version).put("minutes", it.durationMinutes).put("remainingMs", it.left())
                .put("quietRuleAcknowledged", it.quietApplied)
        }
    }

    private fun tool(name: String, description: String, fields: Map<String, Any>) = ToolDefinition(name, name, description,
        mapOf("type" to "object", "additionalProperties" to false, "properties" to fields, "required" to fields.keys.toList()))

    companion object {
        const val STATUS = "halo_focus_status"
        const val START = "halo_focus_start"
        const val END = "halo_focus_end"
        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            val operation = when (definition.name) {
                STATUS -> ToolOperation.QUERY
                START -> ToolOperation.CREATE
                END -> ToolOperation.EDIT
                else -> return null
            }
            return ToolDescriptor(id = ToolId(definition.name), definition = definition, category = ToolCategory.HOST_CONTROL,
                keywords = setOf("focus", "time", "timer"), security = if (operation == ToolOperation.QUERY) ToolSecurityPolicy.READ_ONLY
                else ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY, ReplaySafety.NEVER_REPLAY),
                operability = ToolOperability(capabilityId = CapabilityId("halo.focus"), owner = "HaloFocusOwner", operation = operation,
                    resultEncoding = ToolResultEncoding.JSON,
                    resultSchema = mapOf("type" to "object", "properties" to mapOf(
                        "active" to mapOf("type" to "boolean"), "version" to mapOf("type" to "string"),
                        "minutes" to mapOf("type" to "integer"), "remainingMs" to mapOf("type" to "integer"),
                        "quietRuleAcknowledged" to mapOf("type" to "boolean"), "ended" to mapOf("type" to "boolean")),
                        "required" to if (operation == ToolOperation.QUERY) listOf("active") else listOf("active", "version")),
                    sourceVersionArguments = if (definition.name == END) setOf("version") else emptySet(),
                    resultIdFields = if (operation == ToolOperation.QUERY) emptySet() else setOf("version"),
                    limitations = setOf("Foreground unlocked adult phone only. Tool start leaves Android notification behavior unchanged.")))
        }
    }
}
