package com.halo.runtime.cognition.context

fun interface WorkspaceContextSource {
    suspend fun content(includeMemories: Boolean): List<String>

    /**
     * Exact explicit-reference lookup. Implementations that cannot resolve an
     * object by stable id return null rather than substituting another item.
     */
    suspend fun contentFor(objectId: String): String? = null
}
