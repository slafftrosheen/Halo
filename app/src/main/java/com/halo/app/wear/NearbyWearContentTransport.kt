package com.halo.app.wear

import com.halo.runtime.cognition.interaction.InteractionSurface

/** The eligibility check runs immediately before every content transmission. */
class NearbyWearContentTransport(
    private val nearby: suspend (String) -> Boolean,
    private val transmit: suspend (String, String, ByteArray) -> Unit,
) {
    suspend fun sendSurface(nodeId: String, surface: InteractionSurface?): Boolean {
        if (!nearby(nodeId)) return false
        val current = surface?.takeIf { it.expiresAt?.isAfter(java.time.Instant.now()) != false }
        transmit(nodeId, "/halo/surface", WearSurfaceCodec.encode(current))
        return true
    }
}
