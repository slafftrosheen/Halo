package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelMemoryPlannerTest {
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
