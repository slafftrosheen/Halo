package com.halo.runtime.cognition.tools

/** Tools whose results contain existing private workspace content. */
object WorkspaceToolAccess {
    val readTools = setOf("halo_search_notebook", "halo_get_workspace_object", "halo_search_memory",
        "halo_search_files", "halo_get_file_metadata", "halo_read_text_file", "halo_list_reminders", "halo_notification_digest")
}
