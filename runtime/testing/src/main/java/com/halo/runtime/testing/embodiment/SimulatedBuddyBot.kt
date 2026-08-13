package com.halo.runtime.testing.embodiment

import com.halo.sdk.runtime.embodiment.EmbodimentGateway
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind

class SimulatedBuddyBot : EmbodimentGateway {
    val receivedIntents = mutableListOf<EmbodimentIntent>()

    override suspend fun dispatch(intent: EmbodimentIntent) {
        receivedIntents.add(intent)
        // Fake telemetry/execution result would be emitted here
    }
}
