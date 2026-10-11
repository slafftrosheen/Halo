package com.halo.providers.litert.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every directly downloadable artifact must be pinnable and non-moving, and no duplicate identity
 * may exist in the catalog. Gated artifacts must never enter the direct downloader.
 */
class LiteRtCatalogIntegrityTest {

    private val revisionPattern = Regex("[0-9a-f]{40}")
    private val shaPattern = Regex("[0-9a-f]{64}")

    @Test
    fun `every OPEN_DIRECT model pins an immutable revision sha size filename and url`() {
        assertTrue(LiteRtModelCatalog.DIRECT_DOWNLOAD_MODELS.isNotEmpty())
        LiteRtModelCatalog.DIRECT_DOWNLOAD_MODELS.forEach { model ->
            assertEquals(model.id, LiteRtCatalogAccess.OPEN_DIRECT, model.access)
            assertTrue("${model.id} revision", model.revision.matches(revisionPattern))
            assertTrue("${model.id} sha256", model.sha256.matches(shaPattern))
            assertTrue("${model.id} bytes", model.expectedBytes > 0L)
            assertTrue("${model.id} filename", model.filename.endsWith(".litertlm", ignoreCase = true))
            assertTrue("${model.id} url is pinned", model.downloadUrl.contains(model.revision))
            assertFalse("${model.id} url must not be moving", model.downloadUrl.contains("/main/"))
            assertFalse("${model.id} url must not be moving", model.downloadUrl.contains("latest"))
        }
    }

    @Test
    fun `catalog identity is unique across id filename and sha256`() {
        val models = LiteRtModelCatalog.BUILT_IN_MODELS
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.filename.lowercase() }.toSet().size)
        assertEquals(models.size, models.map { it.sha256 }.toSet().size)
    }

    @Test
    fun `duplicate catalog identity is rejected`() {
        val duplicate = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.copy(id = "duplicate-id")
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtModelCatalog.validateCatalog(
                listOf(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4, duplicate),
            )
        }

        val duplicateFilename = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.copy(
            id = "other-id",
            sha256 = "b".repeat(64),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtModelCatalog.validateCatalog(
                listOf(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4, duplicateFilename),
            )
        }

        val duplicateSha = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.copy(
            id = "other-id",
            filename = "other.litertlm",
        )
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtModelCatalog.validateCatalog(
                listOf(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4, duplicateSha),
            )
        }
    }

    @Test
    fun `a catalog with unique identity validates`() {
        LiteRtModelCatalog.validateCatalog(LiteRtModelCatalog.BUILT_IN_MODELS)
    }

    @Test
    fun `gated models are catalogued but excluded from the direct downloader`() {
        LiteRtModelCatalog.IMPORT_ONLY_MODELS.forEach { model ->
            assertFalse(model.directDownloadAllowed)
            assertTrue(model.access != LiteRtCatalogAccess.OPEN_DIRECT)
            assertTrue(LiteRtModelCatalog.DIRECT_DOWNLOAD_MODELS.none { it.id == model.id })
        }
    }

    @Test
    fun `gated identity is still resolvable for imported-file canonicalization`() {
        val gemma = LiteRtModelCatalog.GEMMA3_1B_IT_INT4
        assertEquals(gemma, LiteRtModelCatalog.bySha256(gemma.sha256))
        assertEquals(gemma, LiteRtModelCatalog.byFilename(gemma.filename))
    }

    @Test
    fun `runtime profiles are self-consistent`() {
        LiteRtModelCatalog.BUILT_IN_MODELS.forEach { model ->
            assertTrue("${model.id} maxOutputTokens", model.runtimeProfile.defaultMaxOutputTokens > 0)
            assertTrue("${model.id} ladder", model.runtimeProfile.contextFallbackLadder.isNotEmpty())
            assertTrue(
                "${model.id} ladder descending",
                model.runtimeProfile.contextFallbackLadder ==
                    model.runtimeProfile.contextFallbackLadder.sortedDescending(),
            )
        }
    }

    @Test
    fun `unproven capabilities are not claimed`() {
        LiteRtModelCatalog.BUILT_IN_MODELS.forEach { model ->
            val multimodal = model.id in setOf(LiteRtModelCatalog.GEMMA3N_E2B.id, LiteRtModelCatalog.GEMMA3N_E4B.id,
                LiteRtModelCatalog.GEMMA4_E2B.id, LiteRtModelCatalog.GEMMA4_E4B.id)
            assertEquals("${model.id} vision", multimodal, model.supportsVision)
            assertEquals("${model.id} audio", multimodal, model.supportsAudio)
            if (multimodal) {
                assertEquals(LiteRtToolCallPreference.NONE, model.toolCallPreference)
                assertEquals(LiteRtModelMaturity.EXPERIMENTAL, model.maturity)
                if (model.family == "Gemma3n") assertTrue(model.requiresLicenseAcceptance)
                else assertTrue(model.directDownloadAllowed)
            }
            assertTrue("${model.id} text", model.supportsText)
            assertFalse("${model.id} tools", model.supportsTools)
        }
    }
}
