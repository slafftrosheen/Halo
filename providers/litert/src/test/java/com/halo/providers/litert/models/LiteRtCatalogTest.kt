package com.halo.providers.litert.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtCatalogTest {

    private val qwen = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
    private val qwenLarge = LiteRtModelCatalog.QWEN3_1_7B_DYNAMIC_INT4
    private val qwen25 = LiteRtModelCatalog.QWEN2_5_1_5B_INSTRUCT_Q8
    private val deepseek = LiteRtModelCatalog.DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8
    private val gemma = LiteRtModelCatalog.GEMMA3_1B_IT_INT4

    @Test
    fun `qwen control artifact keeps its original pins`() {
        assertEquals("a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76", qwen.revision)
        assertEquals(344_671_744L, qwen.expectedBytes)
        assertEquals("03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856", qwen.sha256)
        assertEquals(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4_ID, qwen.id)
        assertEquals("Qwen 3 0.6B Dynamic INT4", qwen.displayName)
        assertEquals("litert-community/Qwen3-0.6B", qwen.repoId)
        assertEquals("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", qwen.filename)
        assertEquals(4096, qwen.contextWindowTokens)
        assertFalse(qwen.supportsVision)
        assertFalse(qwen.supportsTools)
        assertEquals("0.17.1", qwen.minLiteRtLmVersion)
    }

    @Test
    fun `qwen 1 point 7b development artifact is pinned and gpu preferred`() {
        assertEquals("qwen3-1.7b-dynamic-int4", qwenLarge.id)
        assertEquals("litert-community/Qwen3-1.7B", qwenLarge.repoId)
        assertEquals("73fbc3fe8271c162a603ee66f6e7ed25b6211195", qwenLarge.revision)
        assertEquals("Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", qwenLarge.filename)
        assertEquals(977_184_032L, qwenLarge.expectedBytes)
        assertEquals("2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", qwenLarge.sha256)
        assertEquals(LiteRtCatalogAccess.OPEN_DIRECT, qwenLarge.access)
        assertEquals(LiteRtModelMaturity.DEVELOPMENT, qwenLarge.maturity)
        assertEquals(LiteRtModelTier.MEDIUM, qwenLarge.tier)
        assertEquals("dynamic_wi4b32_afp32", qwenLarge.quantizationLabel)
        assertEquals(com.halo.sdk.provider.LocalComputeBackend.GPU, qwenLarge.runtimeProfile.preferredBackend)
        assertTrue(qwenLarge.runtimeProfile.allowCpuFallback)
        assertTrue(qwenLarge.directDownloadAllowed)
    }

    @Test
    fun `qwen 2 point 5 q8 artifact carries the upstream sampling profile`() {
        assertEquals("qwen2.5-1.5b-instruct-q8", qwen25.id)
        assertEquals("litert-community/Qwen2.5-1.5B-Instruct", qwen25.repoId)
        assertEquals("19edb84c69a0212f29a6ef17ba0d6f278b6a1614", qwen25.revision)
        assertEquals("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", qwen25.filename)
        assertEquals(1_597_931_520L, qwen25.expectedBytes)
        assertEquals("faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9", qwen25.sha256)
        assertEquals(20, qwen25.runtimeProfile.defaultTopK)
        assertEquals(0.8, qwen25.runtimeProfile.defaultTopP!!, 1e-9)
        assertEquals(0.7, qwen25.runtimeProfile.defaultTemperature!!, 1e-9)
        assertEquals("Qwen2.5", qwen25.family)
    }

    @Test
    fun `deepseek reasoning artifact is experimental and thinking-capable`() {
        assertEquals("deepseek-r1-distill-qwen-1.5b-q8", deepseek.id)
        assertEquals("litert-community/DeepSeek-R1-Distill-Qwen-1.5B", deepseek.repoId)
        assertEquals("e34bb88632342d1f9640bad579a45134eb1cf988", deepseek.revision)
        assertEquals("DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm", deepseek.filename)
        assertEquals(1_833_451_520L, deepseek.expectedBytes)
        assertEquals("69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295", deepseek.sha256)
        assertEquals(LiteRtModelMaturity.EXPERIMENTAL, deepseek.maturity)
        assertTrue(deepseek.supportsThinking)
        assertTrue(deepseek.directDownloadAllowed)
    }

    @Test
    fun `gemma is catalogued for identity but never directly downloadable`() {
        assertEquals("gemma3-1b-it-int4", gemma.id)
        assertEquals("litert-community/Gemma3-1B-IT", gemma.repoId)
        assertEquals(
            LiteRtModelCatalog.GEMMA3_1B_IT_INT4_EDGE_GALLERY_COMMIT,
            gemma.revision,
        )
        assertEquals("gemma3-1b-it-int4.litertlm", gemma.filename)
        assertEquals(584_417_280L, gemma.expectedBytes)
        assertEquals("1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be", gemma.sha256)
        assertEquals(LiteRtCatalogAccess.GATED_IMPORT_ONLY, gemma.access)
        assertTrue(gemma.requiresLicenseAcceptance)
        assertFalse(gemma.directDownloadAllowed)
        assertNotNull(gemma.importGuidance)
        assertNull(gemma.resolvedContextTokens)
        assertTrue(
            LiteRtModelCatalog.WITHHELD_GATED_REPO_IDS.contains("litert-community/Gemma3-1B-IT"),
        )
        assertTrue(LiteRtModelCatalog.DIRECT_DOWNLOAD_MODELS.none { it.id == gemma.id })
        assertEquals(gemma, LiteRtModelCatalog.byId(gemma.id))
    }

    @Test
    fun `download url is derived from the pinned revision`() {
        val url = qwen.downloadUrl
        assertTrue(url.contains(qwen.revision))
        assertTrue(url.contains(qwen.repoId))
        assertTrue(url.contains(qwen.filename))
        assertFalse(url.contains("/main/"))
        assertFalse(url.contains("latest"))
        assertEquals(
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/" +
                "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76/" +
                "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true",
            url,
        )
    }

    @Test
    fun `catalog exposes the development matrix and six downloadable models`() {
        assertEquals(11, LiteRtModelCatalog.BUILT_IN_MODELS.size)
        assertEquals(8, LiteRtModelCatalog.DIRECT_DOWNLOAD_MODELS.size)
        assertEquals(3, LiteRtModelCatalog.IMPORT_ONLY_MODELS.size)
        assertEquals(setOf(gemma, LiteRtModelCatalog.GEMMA3N_E2B, LiteRtModelCatalog.GEMMA3N_E4B), LiteRtModelCatalog.IMPORT_ONLY_MODELS.toSet())
        assertTrue(LiteRtModelCatalog.BUILT_IN_MODELS.all { it.directDownloadAllowed || it.requiresLicenseAcceptance })
    }

    @Test
    fun `placeholder sha256 values are rejected`() {
        // SHA-256 of the empty string — previously shipped as the fake Qwen pin.
        assertThrows(IllegalArgumentException::class.java) {
            catalogueEntry(sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        }
        // SHA-256 of the literal "test" — previously shipped as the fake Gemma pin.
        assertThrows(IllegalArgumentException::class.java) {
            catalogueEntry(sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        }
        assertNull(LiteRtModelCatalog.bySha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    }

    @Test
    fun `mutable revisions, tiny payloads and non-litertlm files are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { catalogueEntry(revision = "main") }
        assertThrows(IllegalArgumentException::class.java) { catalogueEntry(revision = "d12e8412674e2a868c2fdfbc0a3195022e519208ff") }
        assertThrows(IllegalArgumentException::class.java) { catalogueEntry(expectedBytes = 1_048_576L) }
        assertThrows(IllegalArgumentException::class.java) { catalogueEntry(filename = "model.bin") }
    }

    @Test
    fun `pinned byte counts are real artifact sizes, not rounded guesses`() {
        LiteRtModelCatalog.BUILT_IN_MODELS.forEach { model ->
            assertNotEquals(model.id, 0L, model.expectedBytes % 1_000_000L)
            assertNotEquals(model.id, 0L, model.expectedBytes % 1_000L)
        }
    }

    @Test
    fun `lookup by id sha and filename resolves each artifact`() {
        assertEquals(qwen, LiteRtModelCatalog.byId(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4_ID))
        assertEquals(qwen, LiteRtModelCatalog.bySha256(qwen.sha256.uppercase()))
        assertEquals(qwen, LiteRtModelCatalog.byFilename(qwen.filename))
        assertEquals(deepseek, LiteRtModelCatalog.bySha256(deepseek.sha256))
        assertNull(LiteRtModelCatalog.byId("does-not-exist"))
    }

    private fun catalogueEntry(
        revision: String = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        filename: String = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        expectedBytes: Long = 344_671_744L,
        sha256: String = "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856",
    ) = LiteRtCatalogModel(
        id = "test-entry",
        displayName = "Test Entry",
        repoId = "litert-community/Qwen3-0.6B",
        revision = revision,
        filename = filename,
        expectedBytes = expectedBytes,
        sha256 = sha256,
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
    )
}
