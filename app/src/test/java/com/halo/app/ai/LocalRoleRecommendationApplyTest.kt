package com.halo.app.ai

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class LocalRoleRecommendationApplyTest {
    private val ref = AiModelRef("provider.litert.local", "B")
    private val row = LocalRoleCandidateEvidence(LocalRoleEvidenceIdentity(ref, "a".repeat(64), "device", "LITERT", "0.17.1", 1,
        "CPU", 4096, 4, 0, 1), 1L, true, "NORMAL", 100L, 100L, true, 3, 1000L,
        3, 3, 3, 3, true, ToolCallProtocol.HALO_PROMPT, true, false)
    private fun recommendation(role: AiModelRole, evidence: LocalRoleCandidateEvidence = row) =
        LocalRoleRecommendation(role, ref, listOf(evidence), LocalRoleRecommendationConfidence.SINGLE_MEASURED)
    @Test fun verifiedColdModelIsAvailableWithoutLoading() {
        assertTrue(roleRecommendationProviderAvailable(ProviderReadiness.ConfiguredCold))
        assertTrue(roleRecommendationProviderAvailable(ProviderReadiness.Ready))
        assertFalse(roleRecommendationProviderAvailable(ProviderReadiness.Loading))
        assertFalse(roleRecommendationProviderAvailable(ProviderReadiness.Unconfigured))
        assertFalse(roleRecommendationProviderAvailable(null))
    }
    @Test fun explicitChatUsesConversationAuthorityOnly() {
        val selections = mutableListOf<AiModelRef>(); val bindings = mutableListOf<AiRoleBinding>()
        assertTrue(applyMeasuredRecommendation(AiModelRole.CHAT, ref, row, recommendation(AiModelRole.CHAT),
            row.identity.artifactFingerprint, true, selections::add, bindings::add))
        assertEquals(listOf(ref), selections); assertTrue(bindings.isEmpty())
    }
    @Test fun explicitNonChatUsesRoleAuthorityOnly() {
        val selections = mutableListOf<AiModelRef>(); val bindings = mutableListOf<AiRoleBinding>()
        assertTrue(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST),
            row.identity.artifactFingerprint, true, selections::add, bindings::add))
        assertTrue(selections.isEmpty())
        assertEquals(listOf(AiRoleBinding(AiModelRole.FAST, ref.providerId, "B", AiRoleSelection.EXPLICIT)), bindings)
    }
    @Test fun stalePhysicalConfigurationAndEligibilityNeverWriteSettings() {
        val writes = mutableListOf<Any>()
        val changed = row.copy(identity = row.identity.copy(contextTokens = 2048))
        assertFalse(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST), "b".repeat(64), true, writes::add, writes::add))
        assertFalse(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST, changed), row.identity.artifactFingerprint, true, writes::add, writes::add))
        val replaced = row.copy(identity = row.identity.copy(executionBuildIdentity = "replacement"))
        assertFalse(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST, replaced), row.identity.artifactFingerprint, true, writes::add, writes::add))
        assertFalse(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST), row.identity.artifactFingerprint, false, writes::add, writes::add))
        assertFalse(applyMeasuredRecommendation(AiModelRole.FAST, ref, row, recommendation(AiModelRole.FAST).copy(recommended = null), row.identity.artifactFingerprint, true, writes::add, writes::add))
        assertTrue(writes.isEmpty())
    }
}
