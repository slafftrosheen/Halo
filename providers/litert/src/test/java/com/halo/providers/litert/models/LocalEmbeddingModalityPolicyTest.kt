package com.halo.providers.litert.models

import org.junit.Assert.*
import org.junit.Test

class LocalEmbeddingModalityPolicyTest {
    @Test fun embeddingGemma2SeparatesPublisherAndAdapterCapabilities() {
        val status = LocalEmbeddingModalityPolicy.forCatalog(LiteRtModelCatalog.EMBEDDINGGEMMA2_740M)
        assertEquals(4, status.publisherModalities.size)
        assertTrue(status.canEmbed(EmbeddingInputModality.TEXT))
        assertTrue(status.canEmbed(EmbeddingInputModality.IMAGE))
        assertFalse(status.canEmbed(EmbeddingInputModality.AUDIO))
        assertTrue(EmbeddingInputModality.IMAGE in status.nativeAdapterModalities)
        assertTrue(EmbeddingInputModality.AUDIO in status.nativeAdapterModalities)
        assertFalse(EmbeddingInputModality.VIDEO in status.nativeAdapterModalities)
        assertFalse(status.canEmbed(EmbeddingInputModality.VIDEO))
        assertFalse(LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.supportsVision)
    }
    @Test fun textChatModelDoesNotInheritEmbeddingCapabilities() {
        val status = LocalEmbeddingModalityPolicy.forCatalog(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4)
        assertTrue(status.publisherModalities.isEmpty())
        assertTrue(status.wiredModalities.isEmpty())
    }
}
