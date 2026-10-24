package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

/** Explicit integration metadata only. Dispatch, authorization and availability never derive from these names. */
object WorkspaceOperabilityContracts {
    private data class Operation(val owner: String, val operation: ToolOperation, val encoding: ToolResultEncoding = ToolResultEncoding.JSON)
    private val operations = buildMap<String, Operation> {
        fun group(owner: String, operation: ToolOperation, vararg names: String, encoding: ToolResultEncoding = ToolResultEncoding.JSON) {
            names.forEach { put(it, Operation(owner, operation, encoding)) }
        }
        group("NotebookCanvasRepository", ToolOperation.QUERY, "halo_canvas_get")
        group("NotebookCanvasRepository", ToolOperation.CREATE, "halo_canvas_create", "halo_canvas_add_text")
        group("NotebookCanvasRepository", ToolOperation.CREATE, "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate")
        group("NotebookCanvasRepository", ToolOperation.EDIT, "halo_canvas_edit_text", "halo_canvas_move", "halo_canvas_connect")
        group("NotebookCanvasRepository", ToolOperation.EDIT, "halo_canvas_clear_sketch")
        group("NotebookCanvasRepository", ToolOperation.EDIT, "halo_canvas_rename", "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect")
        group("WorkspaceContinuity", ToolOperation.QUERY, "halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status")
        group("WorkspaceContinuity", ToolOperation.PRESENT, "halo_workspace_open_with")
        group("WorkspaceContinuity", ToolOperation.EDIT, "halo_workspace_insert")
        group("WorkspaceContextStore", ToolOperation.SELECT, "halo_workspace_select")
        group("StudioService", ToolOperation.QUERY, "halo_studio_list", "halo_studio_get", "halo_studio_current", "halo_studio_status")
        group("StudioService", ToolOperation.CREATE, "halo_studio_create")
        group("StudioService", ToolOperation.EDIT, "halo_studio_edit", "halo_studio_save", "halo_studio_insert")
        group("StudioService", ToolOperation.EXPORT, "halo_studio_export")
        group("StudioService", ToolOperation.PRESENT, "halo_studio_open", "halo_studio_ai_draft")
        group("WorkspaceFileTools", ToolOperation.QUERY, "halo_file_tool_capabilities", "halo_text_get", "halo_file_batch_status", "halo_pdf_page_info", "halo_pdf_annotation_get")
        group("WorkspaceFileTools", ToolOperation.CREATE, "halo_text_create", "halo_text_import", "halo_image_transform", "halo_image_batch", "halo_image_collage", "halo_pdf_annotation_create")
        group("WorkspaceFileTools", ToolOperation.EDIT, "halo_text_edit", "halo_pdf_annotation_edit", "halo_file_attach_canvas")
        group("WorkspaceFileTools", ToolOperation.EXPORT, "halo_text_export", "halo_pdf_extract_page_image", "halo_pdf_annotation_export_page_image")
        group("WorkspaceFileTools", ToolOperation.PRESENT, "halo_file_tools_open")
        group("NotebookRepository", ToolOperation.CREATE, "halo_create_note", "halo_create_empty_note", "halo_save_screen_to_notebook", encoding = ToolResultEncoding.TEXT)
        group("WorkspaceRepository", ToolOperation.QUERY, "halo_search_notebook", "halo_get_workspace_object", encoding = ToolResultEncoding.TEXT)
        group("StudyUseCases", ToolOperation.CREATE, "halo_study_with_halo", encoding = ToolResultEncoding.TEXT)
        group("StudyUseCases", ToolOperation.QUERY, "halo_get_study_session", encoding = ToolResultEncoding.TEXT)
        group("StudyUseCases", ToolOperation.QUERY, "halo_study_sessions", "halo_study_state", "halo_study_card_get")
        group("StudyUseCases", ToolOperation.SELECT, "halo_study_select_exercise")
        group("StudyUseCases", ToolOperation.CREATE, "halo_study_submit_attempt", "halo_study_card_create")
        group("StudyUseCases", ToolOperation.EDIT, "halo_study_set_preferences", "halo_study_complete", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review")
        group("StudyUseCases", ToolOperation.EDIT, "halo_study_delete", "halo_study_card_delete")
        group("WorkspaceContextStore", ToolOperation.SELECT, "halo_add_to_working_set", "halo_remove_from_working_set", encoding = ToolResultEncoding.TEXT)
    }
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        val operation = operations[definition.name] ?: return null
        val fields = definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val resultFields = when (operation.owner) {
            "NotebookCanvasRepository" -> mapOf("canvasId" to "string", "version" to "string", "nodeId" to "string", "nodeIds" to "array", "edgeId" to "string", "title" to "string", "changed" to "boolean", "nodes" to "array", "edges" to "array", "nodesTruncated" to "boolean", "edgesTruncated" to "boolean")
            "StudioService" -> mapOf("projectId" to "string", "revision" to "integer", "objectId" to "string", "version" to "string", "outputId" to "string", "fileVersion" to "string", "requestId" to "string", "nodeId" to "string")
            "WorkspaceFileTools" -> mapOf("noteId" to "string", "revision" to "integer", "objectId" to "string", "version" to "string", "nodeId" to "string", "requestId" to "string")
            "StudyUseCases" -> mapOf("sessionId" to "string", "version" to "string", "localDataVersion" to "string", "itemId" to "string", "itemVersion" to "string", "attemptId" to "string", "sessions" to "array", "cards" to "array", "saved" to "boolean", "deleted" to "boolean", "graded" to "boolean")
            else -> mapOf("objectId" to "string", "version" to "string", "nodeId" to "string", "requestId" to "string", "resultId" to "string", "resultVersion" to "string")
        }
        val resultIds = when (definition.name) {
            "halo_study_sessions" -> emptySet()
            "halo_study_state", "halo_study_select_exercise", "halo_study_set_preferences", "halo_study_complete", "halo_study_delete" -> setOf("sessionId")
            "halo_study_submit_attempt" -> setOf("sessionId", "attemptId")
            "halo_study_card_get", "halo_study_card_create", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review", "halo_study_card_delete" -> setOf("sessionId", "itemId")
            "halo_canvas_create", "halo_canvas_get" -> setOf("canvasId")
            "halo_canvas_rename" -> setOf("canvasId")
            "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate" -> setOf("canvasId", "nodeId")
            "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup" -> setOf("canvasId", "nodeIds")
            "halo_canvas_add_text", "halo_canvas_edit_text", "halo_canvas_move", "halo_canvas_clear_sketch" -> setOf("canvasId", "nodeId")
            "halo_canvas_connect", "halo_canvas_disconnect" -> setOf("canvasId", "edgeId")
            "halo_studio_create", "halo_studio_get", "halo_studio_edit", "halo_studio_save" -> setOf("projectId")
            "halo_studio_export" -> setOf("outputId")
            "halo_studio_open", "halo_studio_ai_draft", "halo_studio_status", "halo_file_tools_open", "halo_workspace_open_with", "halo_workspace_handoff_status" -> setOf("requestId")
            "halo_workspace_insert", "halo_studio_insert", "halo_file_attach_canvas" -> setOf("nodeId")
            "halo_text_create", "halo_text_import", "halo_text_get", "halo_text_edit", "halo_image_batch", "halo_file_batch_status", "halo_pdf_annotation_create", "halo_pdf_annotation_get", "halo_pdf_annotation_edit" -> setOf("noteId")
            "halo_text_export", "halo_image_transform", "halo_image_collage", "halo_pdf_extract_page_image", "halo_pdf_annotation_export_page_image", "halo_workspace_describe", "halo_workspace_select" -> setOf("objectId")
            else -> emptySet()
        }
        return ToolOperability(capabilityId = CapabilityId(if (operation.operation in setOf(ToolOperation.QUERY, ToolOperation.PRESENT)) "workspace.read" else "workspace.write"), owner = operation.owner,
            operation = operation.operation, resultEncoding = operation.encoding,
            resultSchema = if (operation.encoding == ToolResultEncoding.TEXT) mapOf("type" to "string", "maxLength" to 24000)
                else mapOf("type" to "object", "properties" to resultFields.mapValues { mapOf("type" to listOf(it.value, "null")) },
                    "required" to (resultIds + (if (operation.owner == "NotebookCanvasRepository") setOf("version") else emptySet()) +
                        (if (definition.name == "halo_canvas_clear_sketch") setOf("changed") else emptySet()) +
                        (if (operation.owner == "StudyUseCases" && operation.encoding == ToolResultEncoding.JSON && definition.name != "halo_study_sessions") setOf("version") else emptySet()) +
                        (if (definition.name in setOf("halo_study_state", "halo_study_delete")) setOf("localDataVersion") else emptySet()) +
                        (if ("itemId" in resultIds) setOf("itemVersion") else emptySet())).toList(), "additionalProperties" to true),
            sourceVersionArguments = fields.keys.filterIsInstance<String>().filter { it in setOf("version", "itemVersion", "localDataVersion", "revision", "sourceVersion", "canvasVersion", "expectedRevision") }.toSet(),
            resultIdFields = resultIds,
            limitations = setOf("Voice-final and typed requests share the registered semantic action; metadata is not STT/device acceptance.",
                "Existing permission, Kids, Private/Power and confirmation owners remain mandatory; success truth comes from the result receipt.",
                "No automatic replay of uncertain writes; read the saved object and its revision before continuing."))
    }
}
