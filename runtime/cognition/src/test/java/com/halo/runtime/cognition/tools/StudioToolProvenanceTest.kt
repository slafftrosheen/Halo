package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.RemoteDataCategory
import org.junit.Assert.*
import org.junit.Test

class StudioToolProvenanceTest {
    @Test fun allStudioResultsRetainWorkspaceProvenanceIncludingWritesAndReceipts() {
        val suffixes = listOf("list", "get", "current", "create", "edit", "save", "export", "insert", "open", "ai_draft", "status")
        suffixes.forEach { suffix ->
            val categories = remoteToolResultCategories("halo_studio_$suffix")
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), categories)
        }
    }
}
