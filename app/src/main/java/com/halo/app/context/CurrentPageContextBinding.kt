package com.halo.app.context

import com.halo.app.data.workspace.WorkspaceActionIds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicit selection adapter only. Call from a page-owned cancellable job after a user action. */
class CurrentPageContextBinding(
    private val versionFor: suspend (String) -> String?,
    private val select: suspend (String, String) -> Unit,
) {
    constructor(adapter: AndroidInteractionContextAdapter, versionFor: suspend (String) -> String?) :
        this(versionFor, { id, version -> adapter.selectWorkspace(id, version) })

    suspend fun bind(route: String, objectId: String, expectedVersion: String? = null): String {
        require(WorkspaceActionIds.isValid(objectId)) { "Page source unavailable" }
        val argument = objectArgument(route) ?: error("This page has no supported source context")
        val path = route.substringBefore('?')
        if (path != "file_tools") {
            val routeId = path.substringAfterLast('/')
            require(routeId == "{$argument}" || routeId == objectId) { "Page source changed. Select it again." }
        }
        val version = versionFor(objectId) ?: error("Page source unavailable")
        check(expectedVersion == null || version == expectedVersion) { "Page source changed. Select it again." }
        currentCoroutineContext().ensureActive()
        select(objectId, version)
        return version
    }

    companion object {
        /** Only exact object views; Studio and Study retain their dedicated semantic owners. */
        fun objectArgument(route: String?): String? = when {
            route?.startsWith("notebook/note/") == true -> "noteId"
            route?.startsWith("notebook/canvas/") == true -> "canvasId"
            route?.startsWith("notebook/file/") == true -> "objectId"
            route?.substringBefore('?') == "file_tools" -> "objectId"
            else -> null
        }
    }
}
