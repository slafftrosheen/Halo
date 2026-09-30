package com.halo.runtime.cognition.context

fun interface WorkspaceContextSource {
    suspend fun content(includeMemories: Boolean): List<String>

    /**
     * Exact explicit-reference lookup. Implementations that cannot resolve an
     * object by stable id return null rather than substituting another item.
     */
    /** Stable bindings for actually selected references; legacy implementations may omit IDs. */
    suspend fun entries(includeMemories: Boolean): List<WorkspaceContextEntry> = content(includeMemories).map { WorkspaceContextEntry(null,it) }

    suspend fun contentFor(objectId: String): String? = null
    suspend fun isMemory(objectId: String): Boolean = false
}

data class WorkspaceContextEntry(val objectId: String?, val text: String)
