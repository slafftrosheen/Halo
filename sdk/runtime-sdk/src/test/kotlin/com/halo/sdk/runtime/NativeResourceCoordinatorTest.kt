package com.halo.sdk.runtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NativeResourceCoordinatorTest {

    @Test fun diagnosticAdmissionCannotAskAnActiveOwnerToYield() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val active = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val diagnostic = FakeOwner(NativeResourceKind.HEAVY_LLM)
        assertTrue(coordinator.acquire(active))
        coordinator.mark(active, NativeResourceState.IN_USE, "User turn")
        assertFalse(coordinator.acquire(diagnostic, diagnostic = true))
        assertFalse(active.released)
        assertTrue(coordinator.snapshot.value.first { it.ownerId == active.nativeResourceId }.executing)
        coordinator.release(active)
        assertTrue(coordinator.acquire(diagnostic, diagnostic = true))
        coordinator.release(diagnostic)
    }

    @Test
    fun nextUseHintDoesNotEvictWarmLlmBeforeAsrActuallyAcquires() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        coordinator.updateBudget(NativeResourceBudget(allowConcurrentHeavyweights = false, idleGraceMillis = 60_000))
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)

        assertTrue(coordinator.acquire(llm))
        coordinator.mark(llm, NativeResourceState.RESIDENT, "generation complete")
        coordinator.hint(NativeNextUseHint.EXPECT_ASR)

        assertFalse(llm.released)
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
        assertEquals(NativeResourceState.RESIDENT, coordinator.snapshot.value.single().state)
    }

    @Test
    fun conflictingOwnerIsAskedToYieldAndDiagnosticsTrackTransition() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val firstLlm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val secondLlm = FakeOwner(NativeResourceKind.HEAVY_LLM)

        assertTrue(coordinator.acquire(firstLlm))
        assertEquals(NativeResourceState.RESIDENT, coordinator.snapshot.value.single().state)

        coordinator.mark(firstLlm, NativeResourceState.IN_USE, "generation active")
        assertEquals(NativeResourceState.IN_USE, coordinator.snapshot.value.single().state)

        assertTrue(coordinator.acquire(secondLlm))
        assertTrue(firstLlm.released)
        assertEquals(
            NativeResourceState.RELEASED,
            coordinator.snapshot.value.first { it.ownerId == firstLlm.nativeResourceId }.state
        )
        assertEquals(
            NativeResourceState.RESIDENT,
            coordinator.snapshot.value.first { it.ownerId == secondLlm.nativeResourceId }.state
        )
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
    }

    @Test
    fun busyOwnerBlocksConflictingAcquisitionAndReportsReason() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val firstLlm = FakeOwner(NativeResourceKind.HEAVY_LLM, canRelease = false)
        val secondLlm = FakeOwner(NativeResourceKind.HEAVY_LLM)

        assertTrue(coordinator.acquire(firstLlm))
        coordinator.mark(firstLlm, NativeResourceState.IN_USE, "generation active")
        assertFalse(coordinator.acquire(secondLlm))
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
        assertEquals(
            NativeResourceState.RELEASED,
            coordinator.snapshot.value.first { it.ownerId == secondLlm.nativeResourceId }.state
        )
        assertTrue(
            coordinator.snapshot.value.first { it.ownerId == secondLlm.nativeResourceId }
                .reason?.contains("busy") == true
        )
    }

    @Test
    fun sameKindResidentOwnerIsYieldedWhenIdle() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val firstLlm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val secondLlm = FakeOwner(NativeResourceKind.HEAVY_LLM)

        assertTrue(coordinator.acquire(firstLlm))
        assertTrue(coordinator.acquire(secondLlm))

        assertTrue(firstLlm.released)
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertEquals(
            NativeResourceState.RELEASED,
            coordinator.snapshot.value.first { it.ownerId == firstLlm.nativeResourceId }.state
        )
        assertEquals(
            NativeResourceState.RESIDENT,
            coordinator.snapshot.value.first { it.ownerId == secondLlm.nativeResourceId }.state
        )
    }

    @Test
    fun sameKindBusyOwnerBlocksSecondOwner() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val firstAsr = FakeOwner(NativeResourceKind.HEAVY_ASR, canRelease = false)
        val secondAsr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(firstAsr))
        coordinator.mark(firstAsr, NativeResourceState.IN_USE, "recognition active")
        assertFalse(coordinator.acquire(secondAsr))

        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertEquals(
            NativeResourceState.IN_USE,
            coordinator.snapshot.value.first { it.ownerId == firstAsr.nativeResourceId }.state
        )
        assertEquals(
            NativeResourceState.RELEASED,
            coordinator.snapshot.value.first { it.ownerId == secondAsr.nativeResourceId }.state
        )
        assertTrue(
            coordinator.snapshot.value.first { it.ownerId == secondAsr.nativeResourceId }
                .reason?.contains("busy") == true
        )
    }

    @Test
    fun differentKindsCanRemainResidentTogether() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(llm))
        assertTrue(coordinator.acquire(asr))

        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertEquals(2, coordinator.snapshot.value.size)
    }

    @Test
    fun ownerSpecificIdleGraceOverridesGlobalBudgetGrace() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        coordinator.updateBudget(NativeResourceBudget(idleGraceMillis = 100))
        val asr = FakeOwner(
            nativeResourceKind = NativeResourceKind.HEAVY_ASR,
            policy = WorkloadResourcePolicy(
                workloadClass = RuntimeWorkloadClass.ASR,
                residencyPreference = ResidencyPreference.WARM_EJECTABLE,
                idleGraceMillis = 1_000L
            )
        )

        assertTrue(coordinator.acquire(asr))
        coordinator.mark(asr, NativeResourceState.RESIDENT, "decode finished")
        delay(200)

        // Global budget is 100ms, but ASR policy is 1000ms, so ASR remains resident after 200ms
        assertFalse(asr.released)
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
    }

    @Test
    fun memoryPressureEvictsWarmEjectableAsr() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val asr = FakeOwner(
            nativeResourceKind = NativeResourceKind.HEAVY_ASR,
            policy = WorkloadResourcePolicy(
                workloadClass = RuntimeWorkloadClass.ASR,
                residencyPreference = ResidencyPreference.WARM_EJECTABLE,
                idleGraceMillis = 90_000L
            )
        )

        assertTrue(coordinator.acquire(asr))
        coordinator.mark(asr, NativeResourceState.RESIDENT, "decode finished")

        coordinator.updateBudget(NativeResourceBudget(pressure = NativeMemoryPressure.LOW))
        assertTrue(asr.released)
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
    }

    @Test
    fun expectAsrHintRefreshesResidentAsrWarmExpiryWithoutLoadingIfReleased() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        // Released ASR is not loaded by hint
        coordinator.hint(NativeNextUseHint.EXPECT_ASR)
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))

        // Resident ASR warm expiry is refreshed
        assertTrue(coordinator.acquire(asr))
        coordinator.mark(asr, NativeResourceState.RESIDENT, "decode finished")
        coordinator.hint(NativeNextUseHint.EXPECT_ASR)
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertFalse(asr.released)
    }

    @Test
    fun freshAsrPreemptsLlmExecutionWithoutEvictingItsCache() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        coordinator.updateBudget(NativeResourceBudget(allowConcurrentHeavyweights = false))
        var preempted = false
        var unloaded = false
        val llm = object : NativeResourceOwner {
            override val nativeResourceKind = NativeResourceKind.HEAVY_LLM
            override val nativeResourceId = "test:llm"
            override suspend fun releaseIfIdle(): Boolean { unloaded = true; return true }
            override suspend fun preemptForVoiceInput(): Boolean {
                preempted = true
                coordinator.mark(this, NativeResourceState.RESIDENT, "cancelled compute; retain cache")
                return true
            }
        }
        assertTrue(coordinator.acquire(llm))
        coordinator.mark(llm, NativeResourceState.IN_USE, "superseded generation")
        assertTrue(coordinator.acquire(FakeOwner(NativeResourceKind.HEAVY_ASR)))
        assertTrue(preempted)
        assertFalse(unloaded)
        val cache = coordinator.snapshot.value.first { it.ownerId == llm.nativeResourceId }
        assertEquals(NativeResourceState.RESIDENT, cache.state)
        assertFalse(cache.executionLeaseHeld)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun llmHintRefreshesOnlyIdleWarmResidencyUnderNormalPressure() = kotlinx.coroutines.test.runTest {
        val coordinator = NativeResourceCoordinator(scope = backgroundScope)
        coordinator.updateBudget(NativeResourceBudget(idleGraceMillis = 500))
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        coordinator.acquire(llm)
        coordinator.mark(llm, NativeResourceState.RESIDENT, "idle")
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(400)
        coordinator.hint(NativeNextUseHint.EXPECT_LLM)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(200)
        assertFalse(llm.released)
        testScheduler.advanceTimeBy(301)
        testScheduler.runCurrent()
        assertTrue(llm.released)
    }

    @Test fun llmHintDoesNotExtendOrReviveResidencyUnderPressure() = runBlocking {
        for (pressure in listOf(NativeMemoryPressure.LOW, NativeMemoryPressure.CRITICAL)) {
            val coordinator = NativeResourceCoordinator()
            val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)
            coordinator.acquire(llm)
            coordinator.mark(llm, NativeResourceState.RESIDENT, "idle")
            coordinator.updateBudget(NativeResourceBudget(pressure = pressure))
            coordinator.hint(NativeNextUseHint.EXPECT_LLM)
            assertTrue(llm.released)
            assertFalse(NativeResourceKind.HEAVY_LLM in coordinator.activeKinds())
        }
    }

    private class FakeOwner(
        override val nativeResourceKind: NativeResourceKind,
        private val canRelease: Boolean = true,
        private val policy: WorkloadResourcePolicy = WorkloadResourcePolicy(RuntimeWorkloadClass.TEXT_CHAT, exclusive = true, residencyPreference = ResidencyPreference.WARM_EJECTABLE)
    ) : NativeResourceOwner {
        var released: Boolean = false
        private val uniqueId = UUID.randomUUID().toString()

        override val defaultResourcePolicy: WorkloadResourcePolicy get() = policy

        override val nativeResourceId: String
            get() = nativeResourceKind.name + ":" + uniqueId

        override suspend fun releaseIfIdle(): Boolean {
            if (!canRelease) return false
            released = true
            return true
        }
    }
}
