package com.halo.app.ui.presence

/** Discovery only. Exact routes map to registered typed owners; tools still require their own
 * fresh read/version, scope and approvals. No UI callback or source-version alias is created. */
internal fun shellChildCanonicalTools(route: String): List<String> = when (route) {
    "settings" -> listOf("halo_get_preferences", "halo_set_preferences")
    "ai_settings", "provider_setup", "settings/intelligence/providers",
    "settings/intelligence/providers/{providerId}", "settings/intelligence/images/{providerId}",
    "model_browser", "settings/intelligence/models?providerId={providerId}&role={role}",
    "settings/intelligence/roles" -> listOf("halo_provider_catalog", "halo_provider_state",
        "halo_provider_refresh_models", "halo_provider_test_connection", "halo_get_mode_profile", "halo_edit_mode_profile",
        "halo_local_role_state", "halo_local_role_select")
    "settings/intelligence/context" -> listOf("halo_get_mode_profile", "halo_edit_mode_profile")
    "advanced/servers", "advanced/servers/{serverId}" -> listOf("halo_mcp_servers", "halo_mcp_server_state",
        "halo_mcp_server_set_enabled", "halo_mcp_tool_set_enabled", "halo_mcp_refresh_tools")
    "notebook/conversations", "notebook/conversation/{conversationId}" -> listOf("halo_saved_chats_list", "halo_saved_chats_search",
        "halo_saved_chat_get", "halo_saved_chat_trash", "halo_saved_chat_restore", "halo_saved_chat_open")
    "settings/voice/input", "settings/voice/output", "advanced/voices", "voice_settings",
    "settings/voice/elevenlabs" -> listOf("halo_get_voice_configuration", "halo_set_voice_configuration",
        "halo_speech_inventory", "halo_speech_model_action")
    "local_audio_lab", "settings/voice/speech-lab" -> listOf("halo_speech_inventory", "halo_speech_model_action")
    "settings/intelligence/on-device", "local_model_lab" -> listOf("halo_local_setup_status",
        "halo_local_setup_start", "halo_local_setup_pause", "halo_local_setup_rollback", "halo_local_setup_keep_files")
    "memory_learning" -> listOf("halo_get_learning_configuration", "halo_set_learning_configuration")
    "notification_settings" -> listOf("halo_notification_settings_apps", "halo_notification_policy_get",
        "halo_notification_policy_set", "halo_digest_delivery_get", "halo_digest_delivery_set")
    "notifications/{notificationKey}" -> listOf("halo_replyable_notifications", "halo_reply_notification")
    "device/{deviceId}" -> listOf("halo_saved_devices", "halo_saved_device_get", "halo_saved_device_retry",
        "halo_saved_device_connect", "halo_saved_device_disconnect", "halo_saved_device_autoconnect", "halo_saved_device_forget")
    "automation?routineId={routineId}", "automation_review/{routineId}/{suggestionId}" -> listOf(
        "halo_routines_list", "halo_routine_get", "halo_routine_set_enabled", "halo_routine_save_existing",
        "halo_routine_delete", "halo_routine_run_now", "halo_routine_run_status")
    else -> emptyList()
}
