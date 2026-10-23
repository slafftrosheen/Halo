package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.provider.RemoteDataCategory
import org.junit.Assert.*
import org.junit.Test

class WorkspaceContinuityPolicyTest {
    @Test fun handoffResultsStayWorkspaceScopedAndHaveOneNativeSkillOwner() {
        val names = listOf("halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status",
            "halo_workspace_open_with", "halo_workspace_insert", "halo_workspace_select")
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        for (name in names) {
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
            assertEquals("halo.notebook", skills.single { name in it.tools }.id.value)
        }
    }
}
