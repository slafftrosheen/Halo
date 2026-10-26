package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class PersonalConfigurationPolicyTest {
    private fun descriptor(name: String, risk: ToolRiskClass = ToolRiskClass.READ_ONLY) = ToolDescriptor(
        id = ToolId(name), definition = ToolDefinition(name, name, "Local configuration fixture"),
        // Deliberately outside WORKSPACE: category alone must not bypass consent.
        category = ToolCategory.SYSTEM, security = ToolSecurityPolicy(risk = risk))

    @Test fun personalMetadataRequiresItsDeclaredDisclosureRegardlessOfCategory() {
        val gate = DefaultToolExposureGate()
        val names = PersonalConfigurationToolAccess.all + WorkspaceToolAccess.canvasWorkflowTools + "halo_canvas_current"
        assertEquals(72, names.size)
        val localOnly = PersonalConfigurationToolAccess.library + PersonalConfigurationToolAccess.savedChats
        assertTrue(localOnly.containsAll(setOf("halo_library_batch", "halo_library_apply_batch")))
        names.forEach { name ->
            val denied = gate.evaluate(descriptor(name), ToolExposureContext(isLocalTarget = false,
                policy = AiContextPolicy(allowCloudWorkspace = false, allowCloudScreenContext = true)))
            assertEquals(name, ToolExposureDecision.Deny(if (name in localOnly) ToolExposureDenyReason.POLICY_DENIED else ToolExposureDenyReason.REMOTE_WORKSPACE_DISABLED), denied)
            val allowed = gate.evaluate(descriptor(name), ToolExposureContext(isLocalTarget = false,
                policy = AiContextPolicy(allowCloudWorkspace = true, allowCloudScreenContext = false)))
            val expected = when (name) {
                in localOnly -> ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED)
                in PersonalConfigurationToolAccess.currentScreen -> ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_SCREEN_DISABLED)
                else -> ToolExposureDecision.Allow
            }
            assertEquals(name, expected, allowed)
            assertEquals(name, if (name in localOnly) ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED) else ToolExposureDecision.Allow,
                gate.evaluate(descriptor(name), ToolExposureContext(isLocalTarget = false,
                    policy = AiContextPolicy(allowCloudWorkspace = true, allowCloudScreenContext = true))))
            assertEquals(name, ToolExposureDecision.Allow, gate.evaluate(descriptor(name), ToolExposureContext(isLocalTarget = true)))
        }
        // Personal-layout consent cannot silently grant cross-app observation.
        assertEquals(ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_SCREEN_DISABLED),
            gate.evaluate(descriptor("halo_read_screen"), ToolExposureContext(isLocalTarget = false,
                policy = AiContextPolicy(allowCloudWorkspace = true, allowCloudScreenContext = false))))
    }

    @Test fun resultProvenanceIncludesMetadataMutationReceiptsWithoutInspectingTheirText() {
        (PersonalConfigurationToolAccess.all + WorkspaceToolAccess.canvasWorkflowTools + "halo_canvas_current").forEach { name ->
            assertTrue(name, name in WorkspaceToolAccess.readTools)
            val extra = when (name) {
                in PersonalConfigurationToolAccess.currentScreen -> setOf(RemoteDataCategory.SCREEN)
                in PersonalConfigurationToolAccess.savedChats -> setOf(RemoteDataCategory.CONVERSATION)
                else -> emptySet()
            }
            assertEquals(name, setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE) + extra, remoteToolResultCategories(name))
        }
        // Routing follows actual registered identities, not a broad prefix guess.
        assertEquals(setOf(RemoteDataCategory.TOOLS), remoteToolResultCategories("halo_screen_unregistered_example"))
    }

    @Test fun configurationToolsRemainOnDemandAndDoNotExpandLocalBootstrapSchemas() {
        val policy = LocalModelToolPolicy.forProtocol(ToolCallProtocol.HALO_PROMPT)
        assertEquals(setOf("halo_discover_tools", "halo_use_skill", "halo_current_time"), policy.allowedToolIds)
        (PersonalConfigurationToolAccess.all + WorkspaceToolAccess.canvasWorkflowTools + "halo_canvas_current").forEach { name ->
            assertFalse(name, policy.allows(name))
            assertTrue(name, policy.mayExpose(name))
        }
    }

    @Test fun workspaceConsentDoesNotBypassOperatingModeOrAvailableToolsPolicy() {
        val gate = DefaultToolExposureGate()
        val remote = ToolExposureContext(isLocalTarget = false, mode = CognitiveOperatingMode.PLAN,
            policy = AiContextPolicy(allowCloudWorkspace = true))
        assertEquals(ToolExposureDecision.Deny(ToolExposureDenyReason.MODE_DISALLOWS_SIDE_EFFECT),
            gate.evaluate(descriptor("halo_screen_settings", ToolRiskClass.SENSITIVE), remote))
        assertEquals(ToolExposureDecision.Allow, gate.evaluate(descriptor("halo_screen_state"), remote))
        assertEquals(ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED),
            gate.evaluate(descriptor("halo_get_mode_profile"), remote.copy(policy = remote.policy.copy(includeAvailableTools = false))))
    }
}
