package com.halo.runtime.cognition.tools

/** Tools whose results contain existing private workspace content. */
object WorkspaceToolAccess {
    val readTools = setOf("halo_search_notebook", "halo_get_workspace_object", "halo_search_memory",
        "halo_search_files", "halo_get_file_metadata", "halo_read_text_file", "halo_list_reminders", "halo_notification_digest",
        "halo_get_study_session")
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
