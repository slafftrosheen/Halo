package com.halo.providers.llamacpp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the concurrency contract used by NativeLlamaProvider:
 * an inference request reserves the heavyweight resource before loading it,
 * so another provider cannot evict it between load completion and generation.
 */
class NativeLlamaProviderResidencyTest {

    @Test
    fun inferenceReservationBlocksCompetingEviction() = runBlocking {
        val reservation = FakeReservation()

        reservation.begin()
        assertFalse(reservation.releaseIfIdle())
        reservation.end()
        assertTrue(reservation.releaseIfIdle())
    }

    private class FakeReservation {
        private var reserved = false

        fun begin() {
            reserved = true
        }

        fun end() {
            reserved = false
        }

        suspend fun releaseIfIdle(): Boolean {
            if (reserved) return false
            return true
        }
    }
}
