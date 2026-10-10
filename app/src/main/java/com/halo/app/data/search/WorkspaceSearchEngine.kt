package com.halo.app.data.search

import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType

data class WorkspaceSearchQuery(
    val query: String,
    val types: Set<WorkspaceObjectType> = emptySet(),
    val maxHits: Int = 20,
    val canvasId: String? = null,
    val sourceTypes: Set<WorkspaceObjectType> = emptySet(),
)

data class WorkspaceSearchHit(
    val objectId: WorkspaceObjectId,
    val type: WorkspaceObjectType,
    val title: String,
    val snippet: String?,
    val score: Float,
    val nodeId: String? = null,
    val sourceObjectId: String? = null,
)

data class WorkspaceSearchResult(
    val query: String,
    val hits: List<WorkspaceSearchHit>
)

interface WorkspaceSearchEngine {
    suspend fun search(query: WorkspaceSearchQuery): WorkspaceSearchResult
    suspend fun indexObject(
        objectId: WorkspaceObjectId,
        type: WorkspaceObjectType,
        title: String,
        content: String,
        updatedAt: Long
    )
    suspend fun removeObject(objectId: WorkspaceObjectId)
}
