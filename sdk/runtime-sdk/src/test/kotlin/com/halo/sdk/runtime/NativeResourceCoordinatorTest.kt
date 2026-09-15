package com.halo.sdk.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResourceCoordinatorTest {

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

    private class FakeOwner(
        override val nativeResourceKind: NativeResourceKind,
        private val canRelease: Boolean = true
    ) : NativeResourceOwner {
        var released: Boolean = false
        private val uniqueId = java.util.UUID.randomUUID().toString()

        override val nativeResourceId: String
            get() = nativeResourceKind.name + ":" + uniqueId

        override suspend fun releaseIfIdle(): Boolean {
            if (!canRelease) return false
            released = true
            return true
        }
    }
}
