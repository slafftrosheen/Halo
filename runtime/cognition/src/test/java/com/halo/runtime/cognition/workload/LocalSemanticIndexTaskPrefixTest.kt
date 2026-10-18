package com.halo.runtime.cognition.workload

import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalSemanticIndexTaskPrefixTest {
    private val sha = "a".repeat(64)

    private class TestStore : SemanticIndexStore {
        private var value: SemanticSnapshot? = null
        override suspend fun read() = value
        override suspend fun replace(snapshot: SemanticSnapshot?) { value = snapshot }
    }

    private class TestEmbedding : EmbeddingProvider {
        override val id = "test-embed"
        val requests = mutableListOf<EmbeddingRequest>()
        override suspend fun embed(request: EmbeddingRequest): EmbeddingBatch {
            requests += request
            return EmbeddingBatch(EmbeddingSpace(request.modelSha256, request.dimension, true,
                "litertlm/0.18.0", request.preprocessingIdentity),
                request.texts.map { listOf(1f) }, System.currentTimeMillis())
        }
    }

    @Test fun embeddingGemma2UsesDifferentDocumentAndQueryInstructions() = runBlocking {
        val provider = TestEmbedding()
        val index = LocalSemanticIndex(provider, TestStore(), EmbeddingSpace(sha, 1, true,
            "litertlm/0.18.0", "embeddinggemma2-search-asymmetric-v1"),
            { false }, { _, _, _ -> true })
        index.synchronize("personal", listOf(SemanticSource(
            "note:one", "v1", "personal", "  red apples  ", "Notebook", 1L)), true)
        val results = index.query("personal", "  where are apples?  ", true)
        assertEquals(1, results.size)
        assertEquals("task: search result | text: red apples", provider.requests[0].texts.single())
        assertEquals("task: search query | text: where are apples?", provider.requests[1].texts.single())
        assertTrue(provider.requests.all { it.preprocessingIdentity == "embeddinggemma2-search-asymmetric-v1" })
    }

    @Test fun legacyEmbeddingSpaceDoesNotClaimGemmaTaskPrefixes() = runBlocking {
        val provider = TestEmbedding()
        val index = LocalSemanticIndex(provider, TestStore(), EmbeddingSpace(sha, 1, true,
            "litertlm/0.18.0", "text-special-tokens-v1"), { false }, { _, _, _ -> true })
        index.synchronize("personal", listOf(SemanticSource("note:one", "v1", "personal",
            "hello", "Notebook", 1L)), true)
        index.query("personal", "query", true)
        assertEquals(listOf("hello"), provider.requests[0].texts)
        assertEquals(listOf("query"), provider.requests[1].texts)
    }

    @Test fun incompatiblePreprocessingIdentityRejectsVectorReuse() = runBlocking {
        val store = TestStore()
        val plain = TestEmbedding()
        val old = LocalSemanticIndex(plain, store, EmbeddingSpace(sha, 1, true,
            "litertlm/0.18.0", "text-special-tokens-v1"), { false }, { _, _, _ -> true })
        old.synchronize("personal", listOf(SemanticSource("note:one", "v1", "personal",
            "hello", "Notebook", 1L)), true)
        val updated = TestEmbedding()
        val next = LocalSemanticIndex(updated, store, EmbeddingSpace(sha, 1, true,
            "litertlm/0.18.0", "embeddinggemma2-search-asymmetric-v1"), { false }, { _, _, _ -> true })
        assertTrue(next.query("personal", "hello", true).isEmpty())
        assertTrue(updated.requests.isEmpty())
    }
}
