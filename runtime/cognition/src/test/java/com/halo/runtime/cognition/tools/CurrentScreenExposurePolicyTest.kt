package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class CurrentScreenExposurePolicyTest {
    // Deliberately SYSTEM: canonical identity must prevent category-based disclosure bypass.
    private val descriptor = ToolDescriptor(id = ToolId("halo_current_screen"),
        definition = ToolDefinition("halo_current_screen", "halo_current_screen", "Current page metadata"),
        category = ToolCategory.SYSTEM, security = ToolSecurityPolicy.READ_ONLY)

    @Test fun remoteExposureRequiresBothScreenAndWorkspaceAndRechecksRevocation() {
        val gate = DefaultToolExposureGate()
        for (screen in listOf(false, true)) for (workspace in listOf(false, true)) {
            val decision = gate.evaluate(descriptor, ToolExposureContext(isLocalTarget = false,
                policy = AiContextPolicy(allowCloudScreenContext = screen, allowCloudWorkspace = workspace)))
            assertEquals("screen=$screen workspace=$workspace", screen && workspace, decision == ToolExposureDecision.Allow)
        }
        val allowed = AiContextPolicy(allowCloudScreenContext = true, allowCloudWorkspace = true)
        assertEquals(ToolExposureDecision.Allow, gate.evaluate(descriptor, ToolExposureContext(isLocalTarget = false, policy = allowed)))
        assertTrue(gate.evaluate(descriptor, ToolExposureContext(isLocalTarget = false,
            policy = allowed.copy(allowCloudWorkspace = false))) is ToolExposureDecision.Deny)
        assertTrue(gate.evaluate(descriptor, ToolExposureContext(isLocalTarget = false,
            policy = allowed.copy(allowCloudScreenContext = false))) is ToolExposureDecision.Deny)
        assertEquals(ToolExposureDecision.Allow, gate.evaluate(descriptor, ToolExposureContext(isLocalTarget = true,
            policy = AiContextPolicy.CONSERVATIVE)))
    }
    @Test fun resultProvenanceRetainsBothCategoriesForLaterRemoteTransfer() {
        assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.SCREEN, RemoteDataCategory.WORKSPACE),
            remoteToolResultCategories("halo_current_screen"))
    }
    @Test fun localLibraryAndSavedHistoryAreNotAdvertisedRemotelyEvenWithBroadCategoryConsent() {
        val gate = DefaultToolExposureGate()
        val remote = ToolExposureContext(isLocalTarget = false,
            policy = AiContextPolicy(allowCloudScreenContext = true, allowCloudWorkspace = true, allowCloudMemories = true))
        (PersonalConfigurationToolAccess.library + PersonalConfigurationToolAccess.savedChats).forEach { name ->
            val tool = descriptor.copy(id = ToolId(name), definition = ToolDefinition(name, name, "Local owner"))
            assertTrue(name, gate.evaluate(tool, remote) is ToolExposureDecision.Deny)
        }
        PersonalConfigurationToolAccess.savedChats.forEach { name ->
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE, RemoteDataCategory.CONVERSATION), remoteToolResultCategories(name))
        }
        (PersonalConfigurationToolAccess.mcpSettings + PersonalConfigurationToolAccess.localRoles).forEach { name ->
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
        }
    }
}
