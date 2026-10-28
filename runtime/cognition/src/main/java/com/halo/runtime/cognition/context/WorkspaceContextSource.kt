package com.halo.runtime.cognition.context

fun interface WorkspaceContextSource {
    suspend fun content(includeMemories: Boolean): List<String>
    /** Canonical hosts must validate an inline excerpt against its captured source revision. */
    val resolvedTextRequiresVersion: Boolean get() = false

    /**
     * Exact explicit-reference lookup. Implementations that cannot resolve an
     * object by stable id return null rather than substituting another item.
     */
    /** Stable bindings for actually selected references; legacy implementations may omit IDs. */
    suspend fun entries(includeMemories: Boolean): List<WorkspaceContextEntry> = content(includeMemories).map { WorkspaceContextEntry(null,it) }

    suspend fun contentFor(objectId: String): String? = null
    suspend fun versionFor(objectId: String): String? = null
    /** Return only reference metadata, never a source body or file bytes. */
    suspend fun referenceMetadata(objectId: String): WorkspaceReferenceMetadata? =
        versionFor(objectId)?.let { WorkspaceReferenceMetadata(objectId, it, null) }
    suspend fun isMemory(objectId: String): Boolean = false
    suspend fun permits(objectId: String, targetIsLocal: Boolean): Boolean = true

    /** Host implementations should override with their canonical read transaction. */
    suspend fun snapshot(objectId: String, expectedVersion: String? = null): WorkspaceContextEntry? {
        val before = versionFor(objectId)
        if (expectedVersion != null && before != expectedVersion) return null
        val text = contentFor(objectId)?.takeIf(String::isNotBlank) ?: return null
        val after = versionFor(objectId)
        if (before != after || expectedVersion != null && after != expectedVersion) return null
        return WorkspaceContextEntry(objectId, text, after)
    }
}

/** Null version means historical/unknown provenance, never a verified current revision. */
data class WorkspaceContextEntry(val objectId: String?, val text: String, val version: String? = null,
    val textAnchor: com.halo.sdk.runtime.study.StudyTextAnchor? = null)

data class WorkspaceReferenceMetadata(val objectId: String, val version: String, val title: String?)
