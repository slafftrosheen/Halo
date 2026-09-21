package com.halo.runtime.cognition.context

fun interface WorkspaceContextSource {
    suspend fun content(includeMemories: Boolean): List<String>
}
