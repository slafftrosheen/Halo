package com.halo.app.data.workspace

import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle

data class WorkspaceFileVersion(val objectId: String, val version: String) {
    init { require(objectId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && version.length in 1..200) }
}

/** Read/source-version adapter; Workspace and the file repository remain the actual owners. */
class WorkspaceFileVersions(private val files: WorkspaceFileRepository, private val workspace: WorkspaceRepository) {
    suspend fun current(source: WorkspaceFileVersion): WorkspaceFile {
        require(workspace.get(WorkspaceObjectId(source.objectId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
        return requireNotNull(files.getFile(WorkspaceObjectId(source.objectId))).also {
            require(it.version == source.version && it.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL))
        }
    }
    suspend fun pin(source: WorkspaceFileVersion, maxBytes: Long = 20_000_000): WorkspaceFile {
        current(source)
        val pinned = files.pinLocalCopy(WorkspaceObjectId(source.objectId), source.version, maxBytes)
        require(pinned.ownership == ContentOwnership.HALO_OWNED && pinned.sha256 != null)
        return current(WorkspaceFileVersion(source.objectId, requireNotNull(pinned.version)))
    }
}
