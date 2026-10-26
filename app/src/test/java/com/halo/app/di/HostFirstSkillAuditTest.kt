package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class HostFirstSkillAuditTest {
    @Test fun everyBundledSkillResolvesThroughTheSharedAuthorityWithoutHome() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        initializeUnitTestWorkManager(context)
        ManualDependencyGraph.init(context)
        val audit = ManualDependencyGraph.skillRuntimeAudit.inspect()
        println("HALO_SKILL_RUNTIME_AUDIT skills=${audit.skillCount} enabled=${audit.enabledSkillCount} tools=${audit.toolCount} orphans=${audit.orphanToolIds.size} unresolved=${audit.unresolvedToolIds.size} duplicates=${audit.duplicateToolIds.size} permissionMismatches=${audit.permissionMismatches.size}")
        assertTrue("Audit problems: orphans=${audit.orphanToolIds}; unresolved=${audit.unresolvedToolIds}; duplicates=${audit.duplicateToolIds}; permissions=${audit.permissionMismatches}",
            audit.orphanToolIds.isEmpty() && audit.unresolvedToolIds.isEmpty() &&
                audit.duplicateToolIds.isEmpty() && audit.permissionMismatches.isEmpty())
    }

    @Test fun currentPageDiscoveryResolvesToGovernedNativeOwnersWithoutHome() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        initializeUnitTestWorkManager(context)
        ManualDependencyGraph.init(context)
        val registry = ManualDependencyGraph.toolRegistry
        val routes = listOf("settings", "settings/intelligence/context", "settings/intelligence/roles",
            "settings/voice/input", "memory_learning", "notification_settings", "device/{deviceId}",
            "automation?routineId={routineId}", "advanced/servers/{serverId}", "notebook/conversation/{conversationId}")
        val discovered = routes.flatMap { com.halo.app.ui.presence.shellChildCanonicalTools(it) }.toSet() +
            com.halo.app.di.tools.host.NotebookLibraryTools.NAMES +
            com.halo.app.di.tools.host.NotebookOrganizationTools.NAMES +
            com.halo.app.di.tools.host.NotebookBatchTools.NAMES + "halo_current_screen"
        discovered.forEach { name -> assertTrue("Unregistered current-page tool: $name", registry.descriptor(name) != null) }
        val problems = registry.operabilityReport(listOf(com.halo.runtime.cognition.tools.ToolFeatureOperability("interface62", discovered)))
        assertTrue(problems.toString(), problems.isEmpty())
        val protected = com.halo.app.di.tools.host.SavedChatTools.names + setOf("halo_library_organize", "halo_library_apply_batch",
            "halo_mcp_server_set_enabled", "halo_mcp_tool_set_enabled", "halo_mcp_refresh_tools", "halo_local_role_select")
        protected.forEach { name ->
            val descriptor = requireNotNull(registry.descriptor(name))
            org.junit.Assert.assertEquals(name, com.halo.runtime.cognition.tools.ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
            org.junit.Assert.assertEquals(name, com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            assertTrue(name, descriptor.requirements.any { it is com.halo.runtime.cognition.tools.ToolRequirement.Capability })
        }
    }
}
