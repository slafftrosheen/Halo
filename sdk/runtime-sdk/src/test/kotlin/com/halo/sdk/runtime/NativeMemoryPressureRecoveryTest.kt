package com.halo.sdk.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeMemoryPressureRecoveryTest {
    @Test fun recoversWithoutActivityRestartAndUsesHysteresis() = runTest {
        var memory = NativeMemorySample(100, 1000, false, 100)
        val published = mutableListOf<NativeMemoryPressure>()
        val recovery = NativeMemoryPressureRecovery(backgroundScope, { memory }, { _, next, _, _ -> published += next })
        recovery.update(NativeMemoryPressure.LOW)
        memory = memory.copy(availableBytes = 120)
        advanceTimeBy(5000); runCurrent()
        assertEquals(NativeMemoryPressure.LOW, published.last())
        memory = memory.copy(availableBytes = 200)
        advanceTimeBy(15000); runCurrent()
        assertEquals(NativeMemoryPressure.NORMAL, published.last())
        val count = published.size
        advanceTimeBy(60000); runCurrent()
        assertEquals(count, published.size)
    }
    @Test fun supersededProbeCannotOverwriteCriticalAndPollingIsBounded() = runTest {
        var memory = NativeMemorySample(200, 1000, false, 100)
        val published = mutableListOf<NativeMemoryPressure>()
        val recovery = NativeMemoryPressureRecovery(backgroundScope, { memory }, { _, next, _, _ -> published += next })
        recovery.update(NativeMemoryPressure.LOW)
        advanceTimeBy(4000)
        memory = memory.copy(lowMemory = true)
        recovery.update(NativeMemoryPressure.CRITICAL)
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf(NativeMemoryPressure.LOW, NativeMemoryPressure.CRITICAL), published)
        advanceTimeBy(50000); runCurrent()
        assertEquals(NativeMemoryPressure.CRITICAL, published.last())
        val count = published.size
        advanceTimeBy(60000); runCurrent()
        assertEquals(count, published.size)
    }
    @Test fun asrReacquisitionAfterRecoveryReceivesFullGraceAndRealLowEvicts() = runTest {
        val coordinator = NativeResourceCoordinator(scope = backgroundScope)
        val owner = object : NativeResourceOwner {
            override val nativeResourceKind = NativeResourceKind.HEAVY_ASR
            override val defaultResourcePolicy = WorkloadResourcePolicy(RuntimeWorkloadClass.ASR,
                residencyPreference = ResidencyPreference.WARM_EJECTABLE, idleGraceMillis = 90_000)
            override suspend fun releaseIfIdle() = true
        }
        coordinator.acquire(owner)
        coordinator.mark(owner, NativeResourceState.RESIDENT, "idle")
        coordinator.updateBudget(NativeResourceBudget(pressure = NativeMemoryPressure.LOW))
        assertEquals(NativeResourceState.RELEASED, coordinator.snapshot.value.single().state)
        coordinator.updateBudget(NativeResourceBudget(pressure = NativeMemoryPressure.NORMAL))
        coordinator.acquire(owner)
        coordinator.mark(owner, NativeResourceState.RESIDENT, "idle after recovery")
        advanceTimeBy(30_000); runCurrent()
        assertEquals(NativeResourceState.RESIDENT, coordinator.snapshot.value.single().state)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(NativeResourceState.RELEASED, coordinator.snapshot.value.single().state)
        coordinator.updateBudget(NativeResourceBudget(pressure = NativeMemoryPressure.LOW))
        coordinator.acquire(owner)
        coordinator.mark(owner, NativeResourceState.RESIDENT, "idle under pressure")
        runCurrent()
        assertEquals(NativeResourceState.RELEASED, coordinator.snapshot.value.single().state)
    }
    @Test fun workloadProbeRecoversAfterBoundedRecoveryJobsStop() = runTest {
        var memory = NativeMemorySample(50, 1000, false, 100)
        val coordinator = NativeResourceCoordinator(scope = backgroundScope)
        val recovery = NativeMemoryPressureRecovery(backgroundScope, { memory }, { _, next, _, _ ->
            coordinator.updateBudget(NativeResourceBudget(pressure = next))
        })
        coordinator.budgetReevaluation = { recovery.update(source = "WORKLOAD_PROBE") }
        recovery.update(NativeMemoryPressure.LOW)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(NativeMemoryPressure.LOW, coordinator.budget.value.pressure)
        memory = memory.copy(availableBytes = 500)
        val owner = object : NativeResourceOwner {
            override val nativeResourceKind = NativeResourceKind.HEAVY_ASR
            override suspend fun releaseIfIdle() = true
        }
        assertTrue(coordinator.acquire(owner))
        assertEquals(NativeMemoryPressure.NORMAL, coordinator.budget.value.pressure)
    }

    @Test fun platformCriticalAndExactHysteresisBoundary() = runTest {
        var memory = NativeMemorySample(500, 1000, true, 200)
        val published = mutableListOf<NativeMemoryPressure>()
        val recovery = NativeMemoryPressureRecovery(backgroundScope, { memory }, { _, next, _, _ -> published += next })
        recovery.update()
        assertEquals(NativeMemoryPressure.CRITICAL, published.last())
        memory = memory.copy(lowMemory = false, availableBytes = 300)
        recovery.update()
        assertEquals(NativeMemoryPressure.LOW, published.last())
        memory = memory.copy(availableBytes = 301)
        recovery.update()
        assertEquals(NativeMemoryPressure.NORMAL, published.last())
    }

}
