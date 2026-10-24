package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.provider.RemoteDataCategory
import org.junit.Assert.*
import org.junit.Test

class WorkspaceContinuityPolicyTest {
    @Test fun remoteInkReferencesKeepWorkspacePrivacyAndOneSkillOwner() {
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        for (name in WorkspaceToolAccess.remoteInkTools) {
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
            assertEquals("halo.notebook", skills.single { name in it.tools }.id.value)
        }
    }
    @Test fun handoffResultsStayWorkspaceScopedAndHaveOneNativeSkillOwner() {
        val names = listOf("halo_canvas_rename", "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate", "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect", "halo_canvas_get", "halo_canvas_create", "halo_canvas_add_text", "halo_canvas_edit_text", "halo_canvas_clear_sketch", "halo_canvas_move", "halo_canvas_connect", "halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status",
            "halo_workspace_open_with", "halo_workspace_insert", "halo_workspace_select")
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        for (name in names) {
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
            assertEquals("halo.notebook", skills.single { name in it.tools }.id.value)
        }
    }
}
