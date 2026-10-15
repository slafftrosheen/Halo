package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

class EmbeddingProviderTest {
    private val space = EmbeddingSpace("a".repeat(64), 2, true)
    @Test fun vectorsRequireFiniteValuesCorrectDimensionsAndNormalization() {
        assertTrue(runCatching { EmbeddingBatch(space, listOf(listOf(Float.NaN, 0f)), 1) }.isFailure)
        assertTrue(runCatching { EmbeddingBatch(space, listOf(listOf(1f)), 1) }.isFailure)
        assertTrue(runCatching { EmbeddingBatch(space, listOf(listOf(1f, 1f)), 1) }.isFailure)
        assertEquals(2, EmbeddingBatch(space, listOf(listOf(1f, 0f)), 1).vectors.single().size)
    }
    @Test fun modelOrDimensionChangesInvalidateTheEmbeddingSpace() {
        val result = EmbeddingBatch(space, listOf(listOf(1f, 0f)), 1)
        result.requireCompatible(space)
        assertTrue(runCatching { result.requireCompatible(space.copy(modelSha256 = "b".repeat(64))) }.isFailure)
        assertTrue(runCatching { result.requireCompatible(space.copy(dimension = 3)) }.isFailure)
    }
}
