package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

/** Describes the canonical active-editor bridge; never grants authority or invokes an operation. */
object CanvasWorkflowOperability {
    private val operations = mapOf(
        "halo_canvas_workflow_status" to ToolOperation.QUERY,
        "halo_canvas_image_palette" to ToolOperation.CREATE,
        "halo_canvas_transcribe_file" to ToolOperation.EDIT,
        "halo_canvas_save_reviewed_text" to ToolOperation.CREATE,
        "halo_canvas_edit_reviewed_text" to ToolOperation.EDIT,
        "halo_canvas_discard_reviewed_text" to ToolOperation.EDIT,
        "halo_canvas_export_note" to ToolOperation.EXPORT)
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        val operation = operations[definition.name] ?: return null
        val query = operation == ToolOperation.QUERY
        fun string(max: Int = 200, nullable: Boolean = false): Map<String, Any> =
            mapOf("type" to if (nullable) listOf("string", "null") else "string", "maxLength" to max)
        val fields = linkedMapOf<String, Any>("canvasId" to string(100), "version" to string(30),
            "phase" to mapOf("type" to "string", "enum" to if (query) listOf("IDLE", "BUSY", "REVIEW_REQUIRED") else listOf("SAVED", "REVIEW_REQUIRED", "DISCARDED")),
            "reviewedDraftToken" to string(100, true), "draftHash" to string(64, true),
            "draftCharacters" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 12000))
        if (query) fields.putAll(mapOf("busy" to mapOf("type" to "boolean"), "sourceNodeId" to string(100, true), "sourceFileVersion" to string(200, true)))
        else fields.putAll(mapOf("noteId" to string(100, true), "nodeId" to string(100, true), "edgeId" to string(100, true),
            "navigationTarget" to string(2000, true), "navigationTruth" to mapOf("type" to "string", "enum" to listOf("NOT_DISPATCHED")),
            "studyStarted" to mapOf("type" to "boolean", "enum" to listOf(false))))
        val parameters = definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return ToolOperability(capabilityId = CapabilityId(if (query) "workspace.read" else "workspace.write"),
            owner = "CanvasEditorViewModel", operation = operation, resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to fields, "required" to fields.keys.toList(), "additionalProperties" to false),
            sourceVersionArguments = parameters.keys.filterIsInstance<String>().filter { it in setOf("version", "fileVersion", "reviewedDraftToken", "draftHash") }.toSet(),
            resultIdFields = if (query) setOf("canvasId") else setOf("canvasId", "noteId", "nodeId", "edgeId"),
            limitations = setOf("Requires one attached current Canvas editor; active drafts and view leases are ephemeral and invalidate on detach/process death.",
                "Source/board revisions, source assistant policy and exact existing review draft token/hash are rechecked by the canonical owner.",
                "No transcript/card body or raw media bytes in results; transcription never saves automatically or captures a microphone.",
                "Export confirms a persisted note, with navigation NOT_DISPATCHED; Study setup is a separate canonical open action, never tutor execution or cloud consent.",
                "Mutations retain confirmation, Kids/Private/source policy and never replay uncertain effects; text/voice-final metadata does not attest actual speech/hardware."))
    }
}
