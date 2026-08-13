package com.halo.runtime.testing.embodiment

import com.halo.sdk.runtime.embodiment.EmbodimentGateway
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind

class SimulatedHeyCyan : EmbodimentGateway {
    val receivedIntents = mutableListOf<EmbodimentIntent>()

    override suspend fun dispatch(intent: EmbodimentIntent) {
        receivedIntents.add(intent)
        // Fake camera/media result would be emitted here
    }
}
