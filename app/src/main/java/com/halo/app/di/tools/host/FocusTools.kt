package com.halo.app.di.tools.host

import android.content.Context
import com.halo.app.ui.toolkit.HaloFocusOwner
import com.halo.app.ui.toolkit.HaloFocusSession
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal class FocusTools(context: Context, private val allowed: () -> Boolean) : ToolExecutor {
    private val owner = HaloFocusOwner.get(context)
    override fun definitions() = listOf(
        tool(STATUS, "Read the active halo focus timer and its exact session version.", emptyMap()),
        tool(START, "Start a timed focus session or break. The tool never changes Android notification sounds.",
            mapOf("minutes" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 120),
                "rest" to mapOf("type" to "boolean")), required = listOf("minutes")),
        tool(END, "End the exact active halo focus session. Read its version first.",
            mapOf("version" to versionField)),
        tool(COMPLETE, "End the exact focus session and keep the user-approved one-line reflection on Today. Requires an on-device request and confirmation.",
            mapOf("version" to versionField, "note" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 240))),
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
                    require(args.keys == setOf("minutes") || args.keys == setOf("minutes", "rest"))
                    val n = (args["minutes"] as Number).toDouble()
                    require(n in 1.0..120.0 && n % 1.0 == 0.0 && allowed())
                    receipt(owner.start(n.toInt(), quiet = false, rest = args["rest"] as? Boolean ?: false))
                }
                END, COMPLETE -> {
                    val keepsNote = name == COMPLETE
                    require(args.keys == if (keepsNote) setOf("version", "note") else setOf("version"))
                    val note = if (keepsNote) {
                        require(coroutineContext[RunToolScope]?.targetIsLocal == true)
                        (args["note"] as String).trim().also { require(it.isNotBlank() && it.length <= 240 && it.none(Char::isISOControl)) }
                    } else null
                    val version = args["version"] as String
                    require(allowed() && owner.end(version, note = note))
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
                .put("quietRuleAcknowledged", it.quietApplied).put("rest", it.rest)
        }
    }

    private fun tool(name: String, description: String, fields: Map<String, Any>, required: List<String> = fields.keys.toList()) =
        ToolDefinition(name, name, description,
            mapOf("type" to "object", "additionalProperties" to false, "properties" to fields, "required" to required))

    companion object {
        const val STATUS = "halo_focus_status"
        const val START = "halo_focus_start"
        const val END = "halo_focus_end"
        const val COMPLETE = "halo_focus_complete"
        private val versionField = mapOf("type" to "string", "minLength" to 1, "maxLength" to 40)
        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            val operation = when (definition.name) {
                STATUS -> ToolOperation.QUERY
                START -> ToolOperation.CREATE
                END, COMPLETE -> ToolOperation.EDIT
                else -> return null
            }
            return ToolDescriptor(id = ToolId(definition.name), definition = definition, category = ToolCategory.HOST_CONTROL,
                keywords = setOf("focus", "time", "timer"), security = if (operation == ToolOperation.QUERY) ToolSecurityPolicy.READ_ONLY
                else ToolSecurityPolicy(ToolRiskClass.REVERSIBLE,
                    if (definition.name == COMPLETE) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.USER_POLICY,
                    ReplaySafety.NEVER_REPLAY),
                operability = ToolOperability(capabilityId = CapabilityId("halo.focus"), owner = "HaloFocusOwner", operation = operation,
                    resultEncoding = ToolResultEncoding.JSON,
                    resultSchema = mapOf("type" to "object", "properties" to mapOf(
                        "active" to mapOf("type" to "boolean"), "version" to mapOf("type" to "string"),
                        "minutes" to mapOf("type" to "integer"), "remainingMs" to mapOf("type" to "integer"),
                        "quietRuleAcknowledged" to mapOf("type" to "boolean"), "ended" to mapOf("type" to "boolean"),
                        "rest" to mapOf("type" to "boolean")),
                        "required" to if (operation == ToolOperation.QUERY) listOf("active") else listOf("active", "version")),
                    sourceVersionArguments = if (definition.name in setOf(END, COMPLETE)) setOf("version") else emptySet(),
                    resultIdFields = if (operation == ToolOperation.QUERY) emptySet() else setOf("version"),
                    limitations = setOf("Foreground unlocked adult phone only. Tool start leaves Android notification behavior unchanged.")))
        }
    }
}
