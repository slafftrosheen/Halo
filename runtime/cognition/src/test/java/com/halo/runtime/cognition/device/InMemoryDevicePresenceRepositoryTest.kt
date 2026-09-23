package com.halo.runtime.cognition.device

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class InMemoryDevicePresenceRepositoryTest {
    private val repository = InMemoryDevicePresenceRepository()

    @Test
    fun `presence update persists and is retrievable`() = runBlocking {
        val now = Instant.now()
        val presence = DevicePresence(
            deviceId = "heycyan_123",
            state = DevicePresenceState.READY,
            evidence = setOf(PresenceEvidence.SESSION_READY),
            updatedAt = now
        )

        repository.updatePresence(presence)
        val fetched = repository.getPresence("heycyan_123")

        assertNotNull(fetched)
        assertEquals(DevicePresenceState.READY, fetched?.state)
    }

    @Test
    fun `stale absent presence does not overwrite newer ready state`() = runBlocking {
        val now = Instant.now()
        val readyPresence = DevicePresence(
            deviceId = "heycyan_123",
            state = DevicePresenceState.READY,
            updatedAt = now
        )
        val staleAbsentPresence = DevicePresence(
            deviceId = "heycyan_123",
            state = DevicePresenceState.ABSENT,
            updatedAt = now.minusSeconds(5)
        )

        repository.updatePresence(readyPresence)
        repository.updatePresence(staleAbsentPresence)

        val fetched = repository.getPresence("heycyan_123")
        assertEquals(DevicePresenceState.READY, fetched?.state)
    }
}
