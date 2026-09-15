package com.halo.sdk.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResourceCoordinatorTest {

    @Test
    fun conflictingOwnerIsAskedToYield() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM)
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(llm))
        assertTrue(coordinator.acquire(asr))
        assertTrue(llm.released)
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
    }

    @Test
    fun busyOwnerBlocksConflictingAcquisition() = runBlocking {
        val coordinator = NativeResourceCoordinator()
        val llm = FakeOwner(NativeResourceKind.HEAVY_LLM, canRelease = false)
        val asr = FakeOwner(NativeResourceKind.HEAVY_ASR)

        assertTrue(coordinator.acquire(llm))
        assertFalse(coordinator.acquire(asr))
        assertTrue(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_LLM))
        assertFalse(coordinator.activeKinds().contains(NativeResourceKind.HEAVY_ASR))
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
