package com.halo.app.ai

import com.halo.providers.litert.models.*
import org.junit.Assert.*
import org.junit.Test

/** Issue #39 source regressions. No build or test execution is implied. */
class CuratedLocalCatalogTest {
    private val qwen = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
    private val embedding = LiteRtModelCatalog.EMBEDDINGGEMMA2_740M
    private val other = LiteRtModelCatalog.QWEN2_0_5B_INSTRUCT

    @Test fun discoveryRequiresBothTechnicalRouteAndDeveloperOptIn() {
        assertFalse(CuratedLocalCatalog.allowsDiscovery(false, false))
        assertFalse(CuratedLocalCatalog.allowsDiscovery(false, true))
        assertFalse(CuratedLocalCatalog.allowsDiscovery(true, false))
        assertTrue(CuratedLocalCatalog.allowsDiscovery(true, true))
    }

    @Test fun consumerOffersOnlyReviewedExactEntries() {
        val visible = CuratedLocalCatalog.visible(listOf(other, embedding, qwen), emptySet())
        assertEquals(listOf(qwen, embedding), visible)
    }

    @Test fun doesNotInventAnEntryMissingFromTheCurrentCatalog() {
        assertTrue(CuratedLocalCatalog.visible(emptyList(), emptySet()).isEmpty())
        assertEquals(listOf(qwen), CuratedLocalCatalog.visible(listOf(qwen), emptySet()))
    }

    @Test fun familiarIdOrNameDoesNotQualifyChangedArtifactBytes() {
        assertNull(CuratedLocalCatalog.entry(qwen.copy(sha256 = "1".repeat(64))))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(expectedBytes = qwen.expectedBytes + 1)))
    }

    @Test fun differentSourceOrRevisionMustBeReviewedAgain() {
        assertNull(CuratedLocalCatalog.entry(qwen.copy(repoId = "different/publisher")))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(revision = "1".repeat(40))))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(filename = "replacement.litertlm")))
    }

    @Test fun alteredTaskCapabilitiesOrRuntimeProfileAreNotPromoted() {
        assertNull(CuratedLocalCatalog.entry(qwen.copy(task = LiteRtArtifactTask.EMBEDDING)))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(supportsVision = true)))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(minLiteRtLmVersion = "99.0.0")))
        assertNull(CuratedLocalCatalog.entry(qwen.copy(runtimeProfile = qwen.runtimeProfile.copy(defaultMaxOutputTokens = 1))))
    }

    @Test fun catalogVerifiedFlagIsNotRoleOrLanguageEvidence() {
        assertNull(CuratedLocalCatalog.entry(qwen.copy(maturity = LiteRtModelMaturity.VERIFIED)))
        assertEquals(CuratedLocalRole.INTENT_CANDIDATE, CuratedLocalCatalog.entry(qwen)?.role)
        assertEquals(CuratedLocalRole.LOCAL_SEARCH_CANDIDATE, CuratedLocalCatalog.entry(embedding)?.role)
    }

    @Test fun installedPausedFailedSelectedAndResidentIdsRemainManageable() {
        val owned = listOf("installed", "paused", "failed", "selected", "resident").map { other.copy(id = it) }
        val visible = CuratedLocalCatalog.visible(owned + listOf(qwen), owned.map { it.id }.toSet())
        assertEquals(listOf(qwen) + owned, visible)
        assertTrue(visible.drop(1).all { CuratedLocalCatalog.entry(it) == null })
    }

    @Test fun retainingAChangedEntryDoesNotMakeItCurated() {
        val changed = qwen.copy(sha256 = "1".repeat(64))
        assertEquals(listOf(changed), CuratedLocalCatalog.visible(listOf(changed), setOf(changed.id)))
        assertNull(CuratedLocalCatalog.entry(changed))
    }

    @Test fun duplicateIdsDoNotCreateDuplicateLazyColumnKeys() {
        val visible = CuratedLocalCatalog.visible(listOf(qwen, qwen, other, other), setOf(other.id))
        assertEquals(listOf(qwen, other), visible)
    }

    @Test fun conflictingDuplicateIdsAreNotPresentedAsCurated() {
        val changed = qwen.copy(sha256 = "1".repeat(64))
        assertTrue(CuratedLocalCatalog.visible(listOf(qwen, changed), emptySet()).isEmpty())
        assertEquals(listOf(changed), CuratedLocalCatalog.visible(listOf(qwen, changed), setOf(qwen.id)))
        assertEquals(listOf(changed), CuratedLocalCatalog.visible(listOf(changed, qwen), setOf(qwen.id)))
    }

    @Test fun knownEnglishFirstLimitationUsesFamilyNotDisplayMarketing() {
        assertTrue(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "LFM2.5", displayName = "Multilingual miracle")))
        assertTrue(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "lfm2.5-1.2b-instruct")))
        assertFalse(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "Qwen3", displayName = "lfm2.5")))
        assertFalse(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "not-lfm2.5")))
    }

    @Test fun knownSourceStillWarnsWhenFamilyMetadataIsMissing() {
        assertTrue(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "Unknown",
            repoId = "litert-community/LFM2.5-1.2B-Instruct")))
        assertFalse(CuratedLocalCatalog.isEnglishFirst(other.copy(family = "Unknown",
            repoId = "publisher/not-lfm2.5")))
    }

    @Test fun installedIdentityRequiresCurrentChecksumAndExactByteCount() {
        assertTrue(CuratedLocalCatalog.matchesInstalledArtifact(qwen, qwen.sha256, qwen.expectedBytes, true))
        assertFalse(CuratedLocalCatalog.matchesInstalledArtifact(qwen, "1".repeat(64), qwen.expectedBytes, true))
        assertFalse(CuratedLocalCatalog.matchesInstalledArtifact(qwen, qwen.sha256, qwen.expectedBytes - 1, true))
    }

    @Test fun missingChecksumOrUnverifiedFileCannotEnableSetupSelection() {
        assertFalse(CuratedLocalCatalog.matchesInstalledArtifact(qwen, null, qwen.expectedBytes, true))
        assertFalse(CuratedLocalCatalog.matchesInstalledArtifact(qwen, qwen.sha256, qwen.expectedBytes, false))
    }

    @Test fun speechPacksAndUnknownModelsAreNotInventedAsLiteRtEntries() {
        assertEquals(listOf(qwen, embedding, LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT),
            CuratedLocalCatalog.entries.map { it.artifact })
        assertEquals(CuratedLocalRole.CONVERSATION_CANDIDATE,
            CuratedLocalCatalog.entry(LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT)?.role)
        assertNull(CuratedLocalCatalog.entry(other.copy(id = "supertonic-3-candidate")))
        assertNull(CuratedLocalCatalog.entry(other.copy(id = "lfm2.5")))
    }
}
