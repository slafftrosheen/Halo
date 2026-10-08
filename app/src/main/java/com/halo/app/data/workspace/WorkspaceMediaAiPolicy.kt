package com.halo.app.data.workspace

import com.halo.app.data.AppDatabase
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.MediaAssetRef
import com.halo.sdk.runtime.multimodal.MediaObjectPolicyResolver

/** User configuration only. Captures and probe payloads never enter preferences or backups. */
class WorkspaceMediaAiPolicy(private val database: AppDatabase, private val storage: Storage) : MediaObjectPolicyResolver {
    fun access(objectId: String): WorkspaceAiAccess =
        runCatching { WorkspaceAiAccess.valueOf(storage.getString("workspace_media_ai:$objectId") ?: "LOCAL_ONLY") }
            .getOrDefault(WorkspaceAiAccess.LOCAL_ONLY)
    fun hasExplicitAccess(objectId: String): Boolean = storage.getString("workspace_media_ai:$objectId") != null
    fun setAccess(objectId: String, access: WorkspaceAiAccess) { storage.putString("workspace_media_ai:$objectId", access.name) }

    override suspend fun permits(ref: MediaAssetRef, isLocal: Boolean): Boolean {
        val id = ref.workspaceObjectId ?: return true
        return permitsObject(id, isLocal)
    }
    suspend fun permitsObject(id: String, isLocal: Boolean): Boolean {
        val item = database.notebookDao().getWorkspaceObject(id) ?: return false
        if (item.lifecycle != "ACTIVE") return false
        return when (access(id)) {
            WorkspaceAiAccess.EXCLUDE_FROM_AI -> false
            WorkspaceAiAccess.LOCAL_ONLY -> isLocal
            WorkspaceAiAccess.ALLOW_CONFIGURED_CLOUD -> true
        }
    }
}
