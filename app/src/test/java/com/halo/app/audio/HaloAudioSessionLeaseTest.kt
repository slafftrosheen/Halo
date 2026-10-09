package com.halo.app.audio

import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HaloAudioSessionLeaseTest {
    @Test fun differentEndpointsStillShareOneAndroidSession() = runTest {
        val manager = DefaultResourceLeaseManager()
        val owner = HaloAudioSessionCoordinator(manager)
        val input = owner.acquireInput("listen", "headset-A").getOrThrow()
        assertTrue(owner.acquireOutput("speak", "speaker-B").isFailure)
        assertEquals(1, manager.snapshotState.value.active.size)
        input.release()
        val output = owner.acquireOutput("speak", "speaker-B").getOrThrow()
        output.release()
        assertTrue(manager.snapshotState.value.active.isEmpty())
        assertTrue(manager.snapshotState.value.waiting.isEmpty())
    }
}
