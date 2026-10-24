package com.halo.app.di.tools

import com.halo.app.context.*
import com.halo.app.data.workspace.WorkspaceActionIds
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class CanvasWorkflowTools(private val bridge: CanvasWorkflowBridge, private val canConfigure: () -> Boolean = { false }) : ToolExecutor {
    private val id = mapOf("type" to "string", "pattern" to WorkspaceActionIds.PATTERN)
    private val version = mapOf("type" to "string", "pattern" to "^canvas:[1-9][0-9]{0,9}$")
    private val base = mapOf("canvasId" to id, "version" to version)
    private val source = base + mapOf("nodeId" to id, "fileVersion" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 200))
    private val reviewed = base + mapOf("reviewedDraftToken" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 100),
        "draftHash" to mapOf("type" to "string", "pattern" to "^[a-f0-9]{64}$"))
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String> = fields.keys.toList()) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_canvas_workflow_status", "Read the actual attached Canvas workflow's board version, busy state and existing reviewed-draft token/hash/character count. Never returns transcript, card bodies, image/audio bytes or permission. Missing/ambiguous views reject; draft tokens are ephemeral.", mapOf("canvasId" to id)),
        definition("halo_canvas_image_palette", "Extract six colors offline from the exact image card/file version through the existing editor owner, saving the real source-linked note/card/edge. Exact board version required. Original bytes unchanged; never replay uncertain creation.", source),
        definition("halo_canvas_transcribe_file", "Attempt the exact saved audio source locally using the selected offline recognizer and current Canvas owner. No cloud fallback, microphone capture or automatic save. Returns review-draft metadata only; review the actual text on Canvas before explicit save. Missing model/source setup rejects honestly.", source),
        definition("halo_canvas_save_reviewed_text", "After explicit approval save the exact locally reviewed transcript/OCR draft token+hash at the exact board/source revision through saveReviewedText. No text inference, source replacement or automatic replay. Returns real saved card/edge identity only after persistence.", reviewed),
        definition("halo_canvas_edit_reviewed_text", "Replace the existing local review draft with literal user-supplied text, guarded by its exact token/hash and board/source version. Does not save a card or alter source files. Returns new draft metadata; review again before saving.", reviewed + ("text" to mapOf("type" to "string", "maxLength" to 12000))),
        definition("halo_canvas_discard_reviewed_text", "Discard only the exact existing local review draft token/hash. Source recording/image/board cards remain unchanged. No inferred deletion and no replay.", reviewed),
        definition("halo_canvas_export_note", "Export the exact current board through the existing editor's canonical note export. Linked source versions/AI access are checked; no raw bytes or note bodies returned. Optional startStudy returns the existing Study source/setup target; navigation remains NOT_DISPATCHED. Open the saved note with the existing workspace open action for actual navigation. Never starts tutor inference or grants sharing. Returns real saved note identity; no automatic replay.",
            base + ("startStudy" to mapOf("type" to "boolean")), base.keys.toList()))
    private fun CanvasWorkflowStatus.json() = JSONObject().put("canvasId", canvasId).put("version", version).put("busy", busy)
        .put("phase", if (busy) "BUSY" else if (reviewedDraftToken != null) "REVIEW_REQUIRED" else "IDLE")
        .put("reviewedDraftToken", reviewedDraftToken ?: JSONObject.NULL).put("draftHash", draftHash ?: JSONObject.NULL)
        .put("draftCharacters", draftCharacters).put("sourceNodeId", sourceNodeId ?: JSONObject.NULL).put("sourceFileVersion", sourceFileVersion ?: JSONObject.NULL)
    private fun CanvasWorkflowReceipt.json() = JSONObject().put("canvasId", canvasId).put("version", version).put("phase", phase)
        .put("noteId", noteId ?: JSONObject.NULL).put("nodeId", nodeId ?: JSONObject.NULL).put("edgeId", edgeId ?: JSONObject.NULL)
        .put("reviewedDraftToken", reviewedDraftToken ?: JSONObject.NULL).put("draftHash", draftHash ?: JSONObject.NULL)
        .put("draftCharacters", draftCharacters).put("navigationTarget", navigationTarget ?: JSONObject.NULL).put("navigationTruth", "NOT_DISPATCHED").put("studyStarted", false)
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        val definition = definitions().firstOrNull { it.name == toolCall.functionName } ?: return reject("Unknown Canvas workflow", ToolRejectionKind.TOOL_UNKNOWN)
        val args = toolCall.arguments
        val properties = definition.parameters["properties"] as Map<*, *>
        if (args.keys.any { it !in properties } || !(definition.parameters["required"] as List<*>).all { it in args }) return reject("Use the closed Canvas workflow fields")
        fun text(name: String, max: Int = 200): String = (args[name] as? String ?: throw CanvasWorkflowConflict("Expected exact $name")).also { requireCanvasWorkflow(it.length in 1..max, "Invalid $name") }
        var started = false
        try {
            val mutation = toolCall.functionName != "halo_canvas_workflow_status"
            fun requireAdmission() {
                if (mutation && (context.interactionId.isNullOrBlank() || !canConfigure()))
                    throw SecurityException("Open Halo in a grown-up session with a current interaction before changing Canvas workflows")
            }
            requireAdmission()
            val canvas = text("canvasId", 100); requireCanvasWorkflow(WorkspaceActionIds.isValid(canvas), "Invalid Canvas identity")
            return bridge.withHost(canvas) { host, leaseGuard ->
                val guard: () -> Unit = { leaseGuard(); requireAdmission() }
                val status = host.workflowStatus(); guard()
                if (toolCall.functionName == "halo_canvas_workflow_status") return@withHost ToolExecutionResult.Success(toolCall.id, toolCall.functionName, status.json().toString(), ActionTruth.ACKNOWLEDGED)
                val observed = text("version", 30)
                requireCanvasWorkflow(observed == status.version && !status.busy, "Canvas changed or is busy. Read its workflow status again.")
                if (toolCall.functionName.contains("reviewed_text")) {
                    requireCanvasWorkflow(text("reviewedDraftToken", 100) == status.reviewedDraftToken && text("draftHash", 64) == status.draftHash,
                        "Review draft changed. Inspect the current draft before a fresh request.")
                    if ("text" in args) requireCanvasWorkflow(args["text"] is String && (args["text"] as String).length <= 12000, "Expected literal bounded draft text")
                }
                if ("startStudy" in args) requireCanvasWorkflow(args["startStudy"] is Boolean, "startStudy must be boolean")
                started = true
                val receipt = when (toolCall.functionName) {
                    "halo_canvas_image_palette" -> host.workflowPalette(observed, text("nodeId", 100), text("fileVersion"), guard)
                    "halo_canvas_transcribe_file" -> host.workflowTranscribe(observed, text("nodeId", 100), text("fileVersion"), guard)
                    "halo_canvas_export_note" -> host.workflowExport(observed, args["startStudy"] == true, guard)
                    else -> host.workflowReviewedText(observed, text("reviewedDraftToken", 100), text("draftHash", 64),
                        when (toolCall.functionName) { "halo_canvas_save_reviewed_text" -> "SAVE"; "halo_canvas_edit_reviewed_text" -> "EDIT"; else -> "DISCARD" }, args["text"] as? String, guard)
                }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, receipt.json().toString(),
                    if (receipt.phase == "SAVED") ActionTruth.CONFIRMED else ActionTruth.ACKNOWLEDGED)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (denied: SecurityException) { return reject(denied.message ?: "Canvas permission changed", ToolRejectionKind.PERMISSION_REQUIRED) }
        catch (conflict: CanvasWorkflowConflict) { return reject(conflict.message ?: "Canvas source changed", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: Exception) { return if (started) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Canvas workflow could not finish. Check current draft/Inbox before another request; persisted effects may be uncertain.", completionUnknown = true)
        else reject("Canvas workflow unavailable", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
}
