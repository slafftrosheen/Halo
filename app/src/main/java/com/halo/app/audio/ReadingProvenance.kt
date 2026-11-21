package com.halo.app.audio

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.provider.RemoteDispatchProvenance

/** Capture current authority for an explicit read without granting unknown saved material access. */
internal suspend fun readingProvenance(category: RemoteDataCategory, workspaceIds: Set<String> = emptySet()): RemoteDispatchProvenance? {
    val revision = Graph.privacyModes.revision
    if (!Graph.powerModeEnabled()) return null
    for (workspaceId in workspaceIds) {
        val item = Graph.appDatabase.notebookDao().getWorkspaceObject(workspaceId) ?: return null
        if (item.lifecycle != "ACTIVE" ||
            !(item.type == "NOTE" && !Graph.workspaceMediaAiPolicy.hasExplicitAccess(workspaceId)) &&
            !Graph.workspaceMediaAiPolicy.permitsObject(workspaceId, false)
        ) return null
    }
    if (Graph.privacyModes.revision != revision || !Graph.powerModeEnabled()) return null
    return RemoteDispatchProvenance(setOf(RemoteDataCategory.TEXT, category), revision)
}
