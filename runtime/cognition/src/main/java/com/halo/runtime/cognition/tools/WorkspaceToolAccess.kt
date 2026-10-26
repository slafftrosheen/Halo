package com.halo.runtime.cognition.tools

/** Personal configuration metadata uses the existing Workspace disclosure category. */
object PersonalConfigurationToolAccess {
    val currentScreen = setOf("halo_current_screen")
    val library = setOf("halo_library_view", "halo_library_set_view", "halo_library_organization", "halo_library_organize")
    val savedChats = setOf("halo_saved_chats_list", "halo_saved_chats_search", "halo_saved_chat_get",
        "halo_saved_chat_trash", "halo_saved_chat_restore", "halo_saved_chat_open")
    val mcpSettings = setOf("halo_mcp_servers", "halo_mcp_server_state", "halo_mcp_server_set_enabled",
        "halo_mcp_tool_set_enabled", "halo_mcp_refresh_tools")
    val localRoles = setOf("halo_local_role_state", "halo_local_role_select")
    val preferences = setOf("halo_get_preferences", "halo_set_preferences", "halo_get_mode_profile", "halo_edit_mode_profile")
    val screenLayout = setOf("halo_screen_state", "halo_screen_preview", "halo_screen_apply", "halo_screen_settings",
        "halo_screen_undo", "halo_screen_save_preset", "halo_screen_remove_preset")
    val savedDevices = setOf("halo_saved_devices", "halo_saved_device_get", "halo_device_preferences", "halo_device_thumbnail_quality",
        "halo_saved_device_retry", "halo_saved_device_connect", "halo_saved_device_disconnect", "halo_saved_device_autoconnect", "halo_saved_device_forget")
    val notificationSettings = setOf("halo_notification_settings_apps", "halo_notification_policy_get", "halo_notification_policy_set",
        "halo_digest_delivery_get", "halo_digest_delivery_set")
    val voice = setOf("halo_get_voice_configuration", "halo_set_voice_configuration")
    val learning = setOf("halo_get_learning_configuration", "halo_set_learning_configuration")
    val providers = setOf("halo_provider_catalog", "halo_provider_state", "halo_provider_refresh_models", "halo_provider_test_connection")
    val routines = setOf("halo_routines_list", "halo_routine_get", "halo_routine_run_status", "halo_routine_set_enabled",
        "halo_routine_save_existing", "halo_routine_delete", "halo_routine_run_now")
    val activeStudy = setOf("halo_study_current", "halo_study_session_action")
    val speechInventory = setOf("halo_speech_inventory", "halo_speech_model_action")
    val all = currentScreen + library + savedChats + mcpSettings + localRoles + preferences + screenLayout + savedDevices + notificationSettings + voice + learning + providers + routines + activeStudy + speechInventory
}

/** Tools whose results contain private workspace content or personal configuration metadata. */
object WorkspaceToolAccess {
    val canvasWorkflowTools = setOf("halo_canvas_workflow_status", "halo_canvas_image_palette", "halo_canvas_transcribe_file",
        "halo_canvas_save_reviewed_text", "halo_canvas_edit_reviewed_text", "halo_canvas_discard_reviewed_text", "halo_canvas_export_note")
    val remoteInkTools = setOf("halo_ink_status", "halo_ink_document", "halo_ink_selection", "halo_ink_start", "halo_ink_stop")
    val readTools = setOf("halo_canvas_rename", "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate", "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect", "halo_canvas_get", "halo_canvas_create", "halo_canvas_add_text", "halo_canvas_edit_text", "halo_canvas_clear_sketch", "halo_canvas_move", "halo_canvas_connect", "halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status", "halo_workspace_open_with", "halo_workspace_insert", "halo_workspace_select", "halo_search_notebook", "halo_get_workspace_object", "halo_search_memory",
        "halo_search_files", "halo_get_file_metadata", "halo_read_text_file", "halo_list_reminders", "halo_notification_digest",
        "halo_note_get", "halo_note_read_block", "halo_note_rename", "halo_note_insert_block", "halo_note_edit_block", "halo_note_move_block", "halo_note_indent_block",
        "halo_note_split_block", "halo_note_merge_block", "halo_note_duplicate_block", "halo_note_delete_block", "halo_note_insert_reference", "halo_note_set_reference", "halo_note_replace_content",
        "halo_note_archive", "halo_note_trash", "halo_note_restore", "halo_note_remove_from_inbox", "halo_note_purge", "halo_note_purge_status", "halo_note_purge_retry", "halo_note_attachments", "halo_note_reuse_image", "halo_note_unlink_attachment",
        "halo_get_study_session", "halo_study_with_halo",
        "halo_study_sessions", "halo_study_state", "halo_study_select_exercise", "halo_study_submit_attempt", "halo_study_set_preferences", "halo_study_complete", "halo_study_delete",
        "halo_study_card_get", "halo_study_card_create", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review", "halo_study_card_delete",
        "halo_file_tool_capabilities", "halo_file_tools_open", "halo_text_create", "halo_text_import", "halo_text_get", "halo_text_edit", "halo_text_export", "halo_image_transform", "halo_image_batch", "halo_image_collage", "halo_file_batch_status", "halo_pdf_page_info", "halo_pdf_extract_page_image", "halo_pdf_annotation_create", "halo_pdf_annotation_get", "halo_pdf_annotation_edit", "halo_pdf_annotation_export_page_image", "halo_file_attach_canvas",
        "halo_studio_list", "halo_studio_get", "halo_studio_current", "halo_studio_create", "halo_studio_edit", "halo_studio_save", "halo_studio_export", "halo_studio_insert", "halo_studio_open", "halo_studio_ai_draft", "halo_studio_status",
        "halo_canvas_current", "halo_context_used_sources") + remoteInkTools + PersonalConfigurationToolAccess.all + canvasWorkflowTools
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
    if (toolName in PersonalConfigurationToolAccess.savedChats) add(com.halo.sdk.provider.RemoteDataCategory.CONVERSATION)
    if (toolName in setOf("halo_search_memory", "halo_propose_memory", "halo_context_used_sources")) add(com.halo.sdk.provider.RemoteDataCategory.MEMORIES)
    if (toolName in DefaultToolExposureGate.CLOUD_SCREEN_GATED_TOOLS || toolName.startsWith("halo_appfn_"))
        add(com.halo.sdk.provider.RemoteDataCategory.SCREEN)
}
