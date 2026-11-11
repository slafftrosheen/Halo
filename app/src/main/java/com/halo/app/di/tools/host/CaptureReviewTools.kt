package com.halo.app.di.tools.host

import com.halo.app.capture.ContextualCaptureController
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject

/** Save/discard the actual host-owned review. Never acquires a camera or reads image bytes. */
class CaptureReviewTools(private val owner: () -> ContextualCaptureController, private val canAccess: () -> Boolean,
    private val remoteAllowed: () -> Boolean) : ToolExecutor {
    override fun definitions() = definitionsList()
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) = ToolExecutionResult.Rejected(
            toolCall.id, name, "Capture review unavailable, access changed or source is stale. Read current review; do not replay an uncertain save.", kind)
        if (name !in NAMES) return reject(ToolRejectionKind.TOOL_UNKNOWN)
        val fields = if (name == STATUS) emptySet() else setOf("captureId", "assetId")
        if (toolCall.arguments.keys != fields) return reject(ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope] ?: return reject(ToolRejectionKind.PERMISSION_REQUIRED)
        val allowed = { canAccess() && (scope.targetIsLocal || remoteAllowed()) }
        if (!allowed()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
        try {
            val controller = owner()
            var objectId: String? = null
            if (name != STATUS) {
                fun value(key: String) = (toolCall.arguments[key] as? String)?.takeIf { it.length in 1..512 && it.none(Char::isISOControl) }
                    ?: throw IllegalArgumentException("Invalid capture source")
                val capture = value("captureId"); val asset = value("assetId")
                if (name == SAVE) objectId = controller.saveExpected(capture, asset, allowed)
                else controller.discardExpected(capture, asset, allowed)
            }
            if (!allowed()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            val current = controller.state.value
            val result = JSONObject().put("available", current != null)
                .put("captureId", current?.captureId ?: JSONObject.NULL).put("assetId", current?.mediaRef?.id ?: JSONObject.NULL)
                .put("objectId", objectId ?: current?.workspaceObjectId ?: JSONObject.NULL)
                .put("status", current?.status?.name ?: "EMPTY").put("quality", current?.quality?.name ?: JSONObject.NULL)
                .put("width", current?.width ?: 0).put("height", current?.height ?: 0)
            return ToolExecutionResult.Success(toolCall.id, name, result.toString(),
                if (name == STATUS) ActionTruth.ACKNOWLEDGED else ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return reject(ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (_: Exception) { return ToolExecutionResult.Failed(toolCall.id, name,
            "Capture review did not complete. Read review and Library before a new action.", completionUnknown = name == SAVE, code = "CAPTURE_REVIEW_UNAVAILABLE") }
    }
    companion object {
        const val STATUS = "halo_capture_review_status"
        const val SAVE = "halo_capture_review_save"
        const val DISCARD = "halo_capture_review_discard"
        val NAMES = setOf(STATUS, SAVE, DISCARD)
        private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 512)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false)
        fun definitionsList() = listOf(
            ToolDefinition(STATUS, STATUS, "Read metadata for the exact image already received in Halo capture review. No image bytes, labels, text, camera, network or inference. Empty means no current image.", schema(emptyMap())),
            ToolDefinition(SAVE, SAVE, "After approval save the exact pending image to local Library through the review owner. Supply captureId and assetId from current review. Preserves the existing edited title/description. Returns actual objectId for existing Notebook/attachment tools. Already saved images return their objectId. No upload or new capture; do not replay uncertain saves.", schema(mapOf("captureId" to id, "assetId" to id))),
            ToolDefinition(DISCARD, DISCARD, "After approval dismiss the exact pending review and release its temporary image. Supply current captureId and assetId. Unsaved bytes are lost; previously saved Library objects remain. No device-file deletion.", schema(mapOf("captureId" to id, "assetId" to id)))
        )
        fun operability(name: String) = ToolOperability(capabilityId = CapabilityId("halo.capture.review"), owner = "ContextualCaptureController",
            operation = when (name) { STATUS -> ToolOperation.QUERY; SAVE -> ToolOperation.CREATE; else -> ToolOperation.EDIT },
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = if (name == STATUS) emptySet() else setOf("captureId", "assetId"),
            resultIdFields = setOf("captureId", "assetId", "objectId"), resultSchema = schema(mapOf(
                "available" to mapOf("type" to "boolean"), "captureId" to mapOf("type" to listOf("string", "null"), "maxLength" to 512),
                "assetId" to mapOf("type" to listOf("string", "null"), "maxLength" to 512), "objectId" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                "status" to mapOf("type" to "string", "maxLength" to 30), "quality" to mapOf("type" to listOf("string", "null"), "maxLength" to 30),
                "width" to mapOf("type" to "integer"), "height" to mapOf("type" to "integer"))),
            limitations = setOf("Only an image already received by the host can be saved. This does not advertise camera or file transport support.",
                "No image bytes, private text or inference are returned. Existing Library operations keep their own access policy."))
    }
}
