package com.halo.engines.execution.capability

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.embodiment.EmbodimentGateway
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import com.halo.runtime.cognition.DeviceTask
import com.halo.engines.execution.service.CapabilityRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockGateway : EmbodimentGateway {
    val receivedIntents = mutableListOf<EmbodimentIntent>()

    override suspend fun dispatch(intent: EmbodimentIntent) {
        receivedIntents.add(intent)
    }
}

class CrossDeviceRoutingTest {

    @Test
    fun `test capability routing resolves to correct embodiment`() = runBlocking {
        val heyCyanGateway = MockGateway()
        val buddyBotGateway = MockGateway()

        val registry = CapabilityRegistry()
        registry.register(CameraCapability(heyCyanGateway))
        registry.register(LocomotionCapability(buddyBotGateway))

        // Action 1: Capture Photo
        val photoTask = DeviceTask(action = "CAPTURE_PHOTO")
        val photoCapability = registry.resolve(photoTask)
        assertTrue(photoCapability is CameraCapability)
        photoCapability?.execute(photoTask)
        
        assertEquals(1, heyCyanGateway.receivedIntents.size)
        assertEquals(IntentKind.CAPTURE_PHOTO, heyCyanGateway.receivedIntents[0].kind)
        assertEquals(0, buddyBotGateway.receivedIntents.size)

        // Action 2: Move Forward
        val moveTask = DeviceTask(action = "MOVE")
        val moveCapability = registry.resolve(moveTask)
        assertTrue(moveCapability is LocomotionCapability)
        moveCapability?.execute(moveTask)

        assertEquals(1, heyCyanGateway.receivedIntents.size) // Unchanged
        assertEquals(1, buddyBotGateway.receivedIntents.size)
        assertEquals(IntentKind.MOVE, buddyBotGateway.receivedIntents[0].kind)
    }
}
