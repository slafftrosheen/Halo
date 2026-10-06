package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

class LocalRoleRecommendationTest {
    private fun row(id: String, ms: Long, chat: Int = 3, reasoning: Int = 3,
        protocol: ToolCallProtocol = ToolCallProtocol.HALO_PROMPT) = LocalRoleCandidateEvidence(
        LocalRoleEvidenceIdentity(AiModelRef("local", id), "a".repeat(64), "device", "LITERT", "0.17.1", 1,
            "CPU", 4096, 4, 0, 1), 1L, true, "NORMAL", 100L, 100L, true, 3, ms,
        chat, 3, reasoning, 3, true, protocol, true, false)
    private fun recommend(role: AiModelRole, rows: List<LocalRoleCandidateEvidence>,
        current: Set<LocalRoleEvidenceIdentity> = rows.map { it.identity }.toSet()) =
        LocalRoleRecommendationEngine.recommend(role, rows, current, rows.map {
            AiRoleCandidate(it.identity.ref, role, true, setOf(AiCapability.TEXT), AiExecutionLocation.ON_DEVICE)
        })
    @Test fun fastQualityPrecedesLatency() {
        val result = recommend(AiModelRole.FAST, listOf(row("a", 900, chat = 1), row("b", 1800)))
        assertEquals("b", result.recommended?.modelId)
        assertEquals(LocalRoleRecommendationConfidence.SINGLE_MEASURED, result.confidence)
    }
    @Test fun comparativeFastUsesWarmMedian() {
        val result = recommend(AiModelRole.FAST, listOf(row("a", 2900), row("b", 1600)))
        assertEquals("b", result.recommended?.modelId)
        assertEquals(LocalRoleRecommendationConfidence.COMPARATIVE, result.confidence)
    }
    @Test fun reasoningRequiresAllAnswers() {
        assertEquals("b", recommend(AiModelRole.REASONING, listOf(row("a", 500, reasoning = 2), row("b", 4000))).recommended?.modelId)
    }
    @Test fun nativeLoopPrecedesLatency() {
        assertEquals("b", recommend(AiModelRole.AGENTIC, listOf(row("a", 1500), row("b", 4000, protocol = ToolCallProtocol.NATIVE))).recommended?.modelId)
    }
    @Test fun allIdentityChangesInvalidateEvidence() {
        val r = row("a", 1000)
        val i = r.identity
        listOf(i.copy(artifactFingerprint = "b".repeat(64)), i.copy(runtimeVersion = "new"),
            i.copy(compatibilityEpoch = 2), i.copy(backend = "GPU"), i.copy(contextTokens = 2048),
            i.copy(threads = 2), i.copy(gpuLayers = 1), i.copy(probeSuiteVersion = 2),
            i.copy(deviceFingerprint = "other"), i.copy(runtime = "other")).forEach {
                assertNull(recommend(AiModelRole.FAST, listOf(r), setOf(it)).recommended)
            }
    }
    @Test fun latestInstabilityAndCriticalPressureExcludeAllRoles() {
        val r = row("a", 1000)
        AiModelRole.entries.forEach { role ->
            assertNull(recommend(role, listOf(r, r.copy(testedAtMs = 2, stable = false))).recommended)
            assertNull(recommend(role, listOf(r.copy(memoryPressure = "CRITICAL"))).recommended)
        }
    }
    @Test fun textArtifactsNeverRecommendVision() {
        assertNull(recommend(AiModelRole.VISION, listOf(row("a", 1000))).recommended)
    }
    @Test fun remoteAndIneligibleAreExcluded() {
        val r = row("a", 1000)
        listOf(AiRoleCandidate(r.identity.ref, AiModelRole.FAST, true, emptySet(), AiExecutionLocation.REMOTE),
            AiRoleCandidate(r.identity.ref, AiModelRole.FAST, false, emptySet(), AiExecutionLocation.ON_DEVICE)).forEach {
                assertNull(LocalRoleRecommendationEngine.recommend(AiModelRole.FAST, listOf(r), setOf(r.identity), listOf(it)).recommended)
            }
    }
    @Test fun backendConfigurationsNeverProduceComparativeConfidence() {
        val a = row("a", 1000)
        val b = row("b", 2000).let { it.copy(identity = it.identity.copy(backend = "GPU")) }
        assertEquals(LocalRoleRecommendationConfidence.NONE, recommend(AiModelRole.FAST, listOf(a, b)).confidence)
        assertNull(recommend(AiModelRole.FAST, listOf(a, b)).recommended)
    }
}
