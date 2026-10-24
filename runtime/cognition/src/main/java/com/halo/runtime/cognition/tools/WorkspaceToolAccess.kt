package com.halo.runtime.cognition.tools

/** Tools whose results contain existing private workspace content. */
object WorkspaceToolAccess {
    val readTools = setOf("halo_canvas_rename", "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate", "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect", "halo_canvas_get", "halo_canvas_create", "halo_canvas_add_text", "halo_canvas_edit_text", "halo_canvas_clear_sketch", "halo_canvas_move", "halo_canvas_connect", "halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status", "halo_workspace_open_with", "halo_workspace_insert", "halo_workspace_select", "halo_search_notebook", "halo_get_workspace_object", "halo_search_memory",
        "halo_search_files", "halo_get_file_metadata", "halo_read_text_file", "halo_list_reminders", "halo_notification_digest",
        "halo_note_get", "halo_note_read_block", "halo_note_rename", "halo_note_insert_block", "halo_note_edit_block", "halo_note_move_block", "halo_note_indent_block",
        "halo_note_split_block", "halo_note_merge_block", "halo_note_duplicate_block", "halo_note_delete_block", "halo_note_insert_reference", "halo_note_set_reference", "halo_note_replace_content",
        "halo_note_archive", "halo_note_trash", "halo_note_restore", "halo_note_remove_from_inbox", "halo_note_purge", "halo_note_purge_status", "halo_note_purge_retry", "halo_note_attachments", "halo_note_reuse_image", "halo_note_unlink_attachment",
        "halo_get_study_session", "halo_study_with_halo",
        "halo_study_sessions", "halo_study_state", "halo_study_select_exercise", "halo_study_submit_attempt", "halo_study_set_preferences", "halo_study_complete", "halo_study_delete",
        "halo_study_card_get", "halo_study_card_create", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review", "halo_study_card_delete",
        "halo_file_tool_capabilities", "halo_file_tools_open", "halo_text_create", "halo_text_import", "halo_text_get", "halo_text_edit", "halo_text_export", "halo_image_transform", "halo_image_batch", "halo_image_collage", "halo_file_batch_status", "halo_pdf_page_info", "halo_pdf_extract_page_image", "halo_pdf_annotation_create", "halo_pdf_annotation_get", "halo_pdf_annotation_edit", "halo_pdf_annotation_export_page_image", "halo_file_attach_canvas",
        "halo_studio_list", "halo_studio_get", "halo_studio_current", "halo_studio_create", "halo_studio_edit", "halo_studio_save", "halo_studio_export", "halo_studio_insert", "halo_studio_open", "halo_studio_ai_draft", "halo_studio_status")
}

/** Content-free provenance from canonical tool identities; never inspect or parse result text. */
internal fun remoteToolResultCategories(toolName: String): Set<com.halo.sdk.provider.RemoteDataCategory> = buildSet {
    add(com.halo.sdk.provider.RemoteDataCategory.TOOLS)
    if (toolName in setOf("halo_delegate_task", "halo_research")) {
        // Includes recovered child results, whose original source distinctions can be unknown.
        add(com.halo.sdk.provider.RemoteDataCategory.WORKSPACE)
        add(com.halo.sdk.provider.RemoteDataCategory.SCREEN)
        add(com.halo.sdk.provider.RemoteDataCategory.MEMORIES)
    }
    if (toolName in WorkspaceToolAccess.readTools) add(com.halo.sdk.provider.RemoteDataCategory.WORKSPACE)
    if (toolName in setOf("halo_search_memory", "halo_propose_memory")) add(com.halo.sdk.provider.RemoteDataCategory.MEMORIES)
    if (toolName in DefaultToolExposureGate.CLOUD_SCREEN_GATED_TOOLS || toolName.startsWith("halo_appfn_"))
        add(com.halo.sdk.provider.RemoteDataCategory.SCREEN)
}
