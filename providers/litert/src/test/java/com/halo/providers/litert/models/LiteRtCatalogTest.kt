package com.halo.providers.litert.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtCatalogTest {

    private val qwen = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4

    @Test
    fun `qwen artifact pins the immutable revision`() {
        assertEquals("a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76", qwen.revision)
    }

    @Test
    fun `qwen artifact pins the exact byte count`() {
        assertEquals(344_671_744L, qwen.expectedBytes)
    }

    @Test
    fun `qwen artifact pins the exact sha256`() {
        assertEquals("03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856", qwen.sha256)
    }

    @Test
    fun `qwen artifact declares runtime compatibility and capabilities`() {
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
    fun `catalog exposes exactly one downloadable model`() {
        assertEquals(1, LiteRtModelCatalog.BUILT_IN_MODELS.size)
        assertEquals(qwen, LiteRtModelCatalog.BUILT_IN_MODELS.single())
    }

    @Test
    fun `gated gemma repository is withheld and not downloadable`() {
        assertTrue(LiteRtModelCatalog.WITHHELD_GATED_REPO_IDS.contains("litert-community/Gemma3-1B-IT"))
        assertTrue(LiteRtModelCatalog.BUILT_IN_MODELS.none { it.repoId.contains("gemma", ignoreCase = true) })
        assertTrue(LiteRtModelCatalog.BUILT_IN_MODELS.none { it.id.contains("gemma", ignoreCase = true) })
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
    fun `pinned byte count is a real artifact size, not a rounded guess`() {
        assertNotEquals(0L, qwen.expectedBytes % 1_000_000L)
        assertNotEquals(0L, qwen.expectedBytes % 1_000L)
    }

    @Test
    fun `lookup by id sha and filename resolves the single artifact`() {
        assertEquals(qwen, LiteRtModelCatalog.byId(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4_ID))
        assertEquals(qwen, LiteRtModelCatalog.bySha256(qwen.sha256.uppercase()))
        assertEquals(qwen, LiteRtModelCatalog.byFilename(qwen.filename))
        assertNull(LiteRtModelCatalog.byId("gemma-3-1b-it-int4"))
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
