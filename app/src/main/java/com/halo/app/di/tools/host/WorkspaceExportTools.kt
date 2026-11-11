package com.halo.app.di.tools.host

import com.halo.app.data.workspace.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject

/** Prepares the same export review as Library. Android owns destination selection/grants. */
class WorkspaceExportTools(private val owner: WorkspaceExternalExportSession, private val versions: WorkspaceFileVersions,
    private val allowed: () -> Boolean) : ToolExecutor {
    override fun definitions() = definitionsList()
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        fun reject(kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, name,
            "Local file export unavailable or source changed. Read the file and export review; the user must choose a destination in Android.", kind)
        if (name !in NAMES) return reject(ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys != if (name == REQUEST) setOf("objectId", "version") else emptySet()) return reject(ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope]
        // No filename/source disclosure to a remote provider through this operational receipt.
        if (scope?.targetIsLocal != true || !allowed()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
        try {
            owner.load()
            if (name == REQUEST) {
                fun text(key: String) = (toolCall.arguments[key] as? String)?.takeIf { it.length in 1..200 && it.none(Char::isISOControl) }
                    ?: throw IllegalArgumentException("Invalid source")
                val file = versions.current(WorkspaceFileVersion(text("objectId"), text("version")))
                check(allowed()); owner.request(file)
            }
            check(allowed())
            val state = owner.state.value
            return ToolExecutionResult.Success(toolCall.id, name, JSONObject().put("available", state != null)
                .put("objectId", state?.id ?: JSONObject.NULL).put("version", state?.version ?: JSONObject.NULL)
                .put("phase", state?.phase?.name ?: "EMPTY").put("outcome", state?.outcome?.name ?: JSONObject.NULL)
                .put("requiresDestinationChoice", state?.phase == ExternalExportPhase.CHOOSE).toString(),
                if (name == REQUEST) ActionTruth.DISPATCHED else ActionTruth.ACKNOWLEDGED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return reject(ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (_: Exception) { return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    companion object {
        const val REQUEST = "halo_file_export_prepare"
        const val STATUS = "halo_file_export_status"
        val NAMES = setOf(REQUEST, STATUS)
        private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false)
        fun definitionsList() = listOf(
            ToolDefinition(REQUEST, REQUEST, "Local route only: after approval prepare export of the exact active Halo-owned Library file using objectId/version. Opens the same export review as Library. The user chooses destination and permission in Android; this call does not write or share. No destination URI accepted, no overwrite/retry of an uncertain previous export. Read status for actual verified outcome.", schema(mapOf("objectId" to id, "version" to id))),
            ToolDefinition(STATUS, STATUS, "Local route only: read the current external-export receipt metadata. VERIFIED requires byte count and SHA-256 readback; UNKNOWN requires inspection, never automatic replay. No destination, filename or file content is returned.", schema(emptyMap()))
        )
        fun operability(name: String) = ToolOperability(capabilityId = CapabilityId("halo.files.external-export"), owner = "WorkspaceExternalExportSession",
            operation = if (name == REQUEST) ToolOperation.EXPORT else ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
            sourceVersionArguments = if (name == REQUEST) setOf("objectId", "version") else emptySet(), resultIdFields = setOf("objectId", "version"),
            resultSchema = schema(mapOf("available" to mapOf("type" to "boolean"), "objectId" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                "version" to mapOf("type" to listOf("string", "null"), "maxLength" to 200), "phase" to mapOf("type" to "string", "maxLength" to 30),
                "outcome" to mapOf("type" to listOf("string", "null"), "maxLength" to 30), "requiresDestinationChoice" to mapOf("type" to "boolean"))),
            limitations = setOf("Android destination choice and URI permission require the human system picker; model arguments cannot supply them.",
                "Local route only. Preparing the review is DISPATCHED, never evidence that bytes were exported."))
    }
}
