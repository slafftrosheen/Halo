package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelMemoryPlannerTest {
    private val mb = 1024L * 1024L

    private fun memory(totalGb: Int, availableGb: Int, lowMemory: Boolean = false) =
        LocalModelMemoryPlanner.MemorySnapshot(
            totalBytes = totalGb.toLong() * 1024L * 1024L * 1024L,
            availableBytes = availableGb.toLong() * 1024L * 1024L * 1024L,
            lowMemory = lowMemory,
        )

    @Test
    fun choosesRequestedContextWhenHeadroomExists() {
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 2L * 1024L * 1024L * 1024L,
            memory = memory(totalGb = 8, availableGb = 6),
            candidates = listOf(
                LocalModelMemoryPlanner.Candidate(contextSize = 4096, gpuLayers = 0),
                LocalModelMemoryPlanner.Candidate(contextSize = 2048, gpuLayers = 0),
            ),
        )

        assertNotNull(plan)
        assertEquals(4096, plan!!.candidate.contextSize)
    }

    @Test
    fun allowsSmallModelOnSixGigabyteDeviceWithAboutOneGigabyteAvailable() {
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 388L * mb,
            memory = LocalModelMemoryPlanner.MemorySnapshot(
                totalBytes = 6L * 1024L * mb,
                availableBytes = 1070L * mb,
                lowMemory = false,
            ),
            candidates = LocalModelMemoryPlanner.defaultCandidates(
                requestedContextSize = 4096,
                requestedGpuLayers = 0,
                accelerator = false,
            ),
        )

        assertNotNull(plan)
        assertEquals(4096, plan!!.candidate.contextSize)
    }

    @Test
    fun fallsBackToSmallerContextWhenRequestedContextDoesNotFit() {
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 4L * 1024L * 1024L * 1024L,
            memory = memory(totalGb = 8, availableGb = 7),
            candidates = listOf(
                LocalModelMemoryPlanner.Candidate(contextSize = 4096, gpuLayers = 0),
                LocalModelMemoryPlanner.Candidate(contextSize = 2048, gpuLayers = 0),
                LocalModelMemoryPlanner.Candidate(contextSize = 1024, gpuLayers = 0),
            ),
        )

        assertNotNull(plan)
        assertEquals(2048, plan!!.candidate.contextSize)
    }

    @Test
    fun rejectsWhenEvenSmallestCandidateExceedsMemoryEnvelope() {
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 7L * 1024L * 1024L * 1024L,
            memory = memory(totalGb = 8, availableGb = 7),
            candidates = listOf(LocalModelMemoryPlanner.Candidate(contextSize = 512, gpuLayers = 0)),
        )

        assertNull(plan)
    }

    @Test
    fun rejectsLowMemoryStateBeforePlanning() {
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 512L * 1024L * 1024L,
            memory = memory(totalGb = 8, availableGb = 7, lowMemory = true),
            candidates = listOf(LocalModelMemoryPlanner.Candidate(contextSize = 1024, gpuLayers = 0)),
        )

        assertNull(plan)
    }

    @Test
    fun estimatesKnownLlamaDimensionsFromKvCacheGeometry() {
        val profile = LocalModelMemoryPlanner.ModelProfile(
            blockCount = 32,
            embeddingLength = 4096,
            attentionHeadCount = 32,
            attentionHeadCountKv = 8,
        )

        // 32 layers * 8 KV heads * 128 head dim * 4096 tokens * 4 bytes,
        // followed by the planner's 1.5x conservative headroom multiplier.
        val expected = 805_306_368L
        assertEquals(expected, LocalModelMemoryPlanner.estimateContextBytes(4096, profile))
    }

    @Test
    fun fallsBackToFixedEstimateWhenAttentionMetadataIsIncomplete() {
        val profile = LocalModelMemoryPlanner.ModelProfile(
            blockCount = 32,
            embeddingLength = 4096,
            attentionHeadCount = 32,
            attentionHeadCountKv = null,
        )

        assertEquals(
            1024L * LocalModelMemoryPlanner.APPROX_BYTES_PER_CONTEXT_TOKEN,
            LocalModelMemoryPlanner.estimateContextBytes(1024, profile),
        )
    }

    @Test
    fun fallsBackWhenArchitectureDimensionsAreInconsistent() {
        val profile = LocalModelMemoryPlanner.ModelProfile(
            blockCount = 32,
            embeddingLength = 4097,
            attentionHeadCount = 32,
            attentionHeadCountKv = 8,
        )

        assertEquals(
            2048L * LocalModelMemoryPlanner.APPROX_BYTES_PER_CONTEXT_TOKEN,
            LocalModelMemoryPlanner.estimateContextBytes(2048, profile),
        )
    }

    @Test
    fun architectureAwareEstimateCanForceContextReduction() {
        val profile = LocalModelMemoryPlanner.ModelProfile(
            blockCount = 32,
            embeddingLength = 4096,
            attentionHeadCount = 32,
            attentionHeadCountKv = 8,
        )
        val plan = LocalModelMemoryPlanner.choose(
            modelBytes = 4L * 1024L * 1024L * 1024L,
            memory = memory(totalGb = 8, availableGb = 7),
            candidates = listOf(
                LocalModelMemoryPlanner.Candidate(contextSize = 4096, gpuLayers = 0),
                LocalModelMemoryPlanner.Candidate(contextSize = 2048, gpuLayers = 0),
            ),
            modelProfile = profile,
        )

        assertNotNull(plan)
        assertEquals(2048, plan!!.candidate.contextSize)
    }

    @Test
    fun defaultCandidatesTryContextReductionAndAcceleratorFallback() {
        val candidates = LocalModelMemoryPlanner.defaultCandidates(
            requestedContextSize = 4096,
            requestedGpuLayers = 32,
            accelerator = true,
        )

        assertEquals(LocalModelMemoryPlanner.Candidate(4096, 32), candidates.first())
        assertEquals(LocalModelMemoryPlanner.Candidate(4096, 16), candidates[1])
        assertEquals(LocalModelMemoryPlanner.Candidate(4096, 8), candidates[2])
        assert(candidates.contains(LocalModelMemoryPlanner.Candidate(2048, 0)))
    }
}
