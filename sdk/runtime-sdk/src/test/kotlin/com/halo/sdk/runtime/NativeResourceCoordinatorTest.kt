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
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(llm))
        assertEquals(NativeResourceState.RESIDENT, coordinator.snapshot.value.single().state)

        coordinator.mark(llm, NativeResourceState.IN_USE, "generation active")
        assertEquals(NativeResourceState.IN_USE, coordinator.snapshot.value.single().state)

        assertTrue(coordinator.acquire(asr))
        assertTrue(llm.released)
        assertEquals(NativeResourceState.RELEASED, coordinator.snapshot.value.first { it.ownerId == llm.nativeResourceId }.state)
        assertEquals(NativeResourceState.RESIDENT, coordinator.snapshot.value.first { it.ownerId == asr.nativeResourceId }.state)
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
    }

    @Test
    fun busyOwnerBlocksConflictingAcquisitionAndReportsReason() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM, canRelease = false)
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(llm))
        coordinator.mark(llm, NativeResourceState.IN_USE, "generation active")
        assertFalse(coordinator.acquire(asr))
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertEquals(NativeResourceState.RELEASED, coordinator.snapshot.value.first { it.ownerId == asr.nativeResourceId }.state)
        assertTrue(coordinator.snapshot.value.first { it.ownerId == asr.nativeResourceId }.reason?.contains("busy") == true)
    }

    private class FakeOwner(
        override val nativeResourceKind: NativeResourceKind,
        private val canRelease: Boolean = true
    ) : NativeResourceOwner {
        var released: Boolean = false

        override suspend fun releaseIfIdle(): Boolean {
            if (!canRelease) return false
            released = true
            return true
        }
    }
}
