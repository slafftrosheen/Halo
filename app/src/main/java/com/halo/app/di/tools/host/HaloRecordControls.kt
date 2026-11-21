package com.halo.app.di.tools.host

import com.halo.app.ui.toolkit.HaloRecordSessionState
import com.halo.app.ui.toolkit.HaloRecordSessions
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject

/** The smartphone user opens the microphone and grants caption output. Tools see control state only. */
class HaloRecordControls(private val canAccess: () -> Boolean) : ToolExecutor {
    override fun definitions(): List<ToolDefinition> = definitionsList()

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val scope = currentCoroutineContext()[RunToolScope]
        if (scope == null || !scope.targetIsLocal || !canAccess())
            return reject(toolCall, "Use foreground grown-up Halo on this phone.", ToolRejectionKind.PERMISSION_REQUIRED)
        return when (toolCall.functionName) {
            STATUS -> {
                if (toolCall.arguments.isNotEmpty()) return reject(toolCall, "This status query takes no arguments.")
                success(toolCall, HaloRecordSessions.state.value, ActionTruth.ACKNOWLEDGED)
            }
            STOP -> {
                if (toolCall.arguments.keys != setOf("sessionToken", "sourceVersion"))
                    return reject(toolCall, "Use the exact token and version from Record status.")
                val token = (toolCall.arguments["sessionToken"] as? String)?.takeIf {
                    it.length == 36 && runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)
                } ?: return reject(toolCall, "Use the exact Record session token.")
                val number = toolCall.arguments["sourceVersion"] as? Number
                    ?: return reject(toolCall, "Use the exact positive Record source version.")
                val revision = number.toLong()
                if (revision <= 0 || revision.toDouble() != number.toDouble())
                    return reject(toolCall, "Use the exact positive Record source version.")
                if (!canAccess())
                    return reject(toolCall, "Record access changed.", ToolRejectionKind.PERMISSION_REQUIRED)
                val requested = HaloRecordSessions.stop(token, revision)
                    ?: return reject(toolCall, "Recording changed. Read its current status.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                success(toolCall, requested, ActionTruth.DISPATCHED)
            }
            else -> reject(toolCall, "Unknown Record control.", ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun success(call: ToolCall, state: HaloRecordSessionState?, truth: ActionTruth) = ToolExecutionResult.Success(
        call.id, call.functionName, JSONObject()
            .put("phase", state?.phase?.name ?: "IDLE")
            .put("sessionToken", state?.sessionToken ?: JSONObject.NULL)
            .put("sourceVersion", state?.sourceVersion ?: 0L)
            .toString(), truth)

    private fun reject(call: ToolCall, message: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
        ToolExecutionResult.Rejected(call.id, call.functionName, message, kind)

    companion object {
        const val STATUS = "halo_record_status"
        const val STOP = "halo_record_stop"
        val NAMES = setOf(STATUS, STOP)
        private val token = mapOf("type" to "string", "minLength" to 36, "maxLength" to 36)
        private val version = mapOf("type" to "integer", "minimum" to 1)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "additionalProperties" to false, "required" to fields.keys.toList())

        fun definitionsList() = listOf(
            ToolDefinition(STATUS, STATUS,
                "Read foreground HaloRecord control state. Returns phase, exact session token and revision. It does not read an audio recording, transcript, speaker evidence, or saved document.",
                schema(emptyMap())),
            ToolDefinition(STOP, STOP,
                "Request that the exact current recording stop. Use halo_record_status to get its token and revision first. STOPPING means stop requested; it does not assert capture, saving or playback success.",
                schema(mapOf("sessionToken" to token, "sourceVersion" to version))),
        )

        fun operability(name: String) = ToolOperability(
            capabilityId = CapabilityId("host.record.foreground"),
            owner = "HaloRecordSessions",
            operation = if (name == STATUS) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            sourceVersionArguments = if (name == STOP) setOf("sessionToken", "sourceVersion") else emptySet(),
            resultIdFields = setOf("sessionToken"),
            resultSchema = schema(mapOf("phase" to mapOf("type" to "string",
                "enum" to listOf("IDLE", "STARTING", "RECORDING", "STOPPING")),
                "sessionToken" to mapOf("type" to listOf("string", "null"), "maxLength" to 36),
                "sourceVersion" to (version + ("minimum" to 0)))),
            limitations = setOf("Microphone permission and recording start are foreground user actions.",
                "Live captions require the user to select and confirm a connected Android presentation display per recording.",
                "No audio, transcript or biometric data is exposed. Stop is requested for an exact ephemeral session."),
        )
    }
}
