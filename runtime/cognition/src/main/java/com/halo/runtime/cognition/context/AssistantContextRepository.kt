package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.ScreenContextSnapshot
import java.util.concurrent.ConcurrentHashMap

/** Process-local, transient assistant context. Nothing here is persisted as memory. */
class AssistantContextRepository {
    private val screens = ConcurrentHashMap<String, ScreenContextSnapshot>()
    private val images = ConcurrentHashMap<String, ImageContext>()

    fun put(interactionId: String, snapshot: ScreenContextSnapshot) {
        screens[interactionId] = snapshot
        evictExpired()
    }

    fun screenFor(interactionId: String): ScreenContextSnapshot? =
        screens[interactionId]?.takeIf { System.currentTimeMillis() - it.capturedAtMs <= MAX_AGE_MS }

    fun putImage(interactionId: String, image: ImageContext) {
        images[interactionId] = image
        evictExpired()
    }

    fun imageFor(interactionId: String): ImageContext? =
        images[interactionId]?.takeIf { System.currentTimeMillis() - it.timestampMs <= MAX_AGE_MS }

    fun clear(interactionId: String) {
        screens.remove(interactionId)
        images.remove(interactionId)
    }

    private fun evictExpired() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        screens.entries.removeIf { it.value.capturedAtMs < cutoff }
        images.entries.removeIf { it.value.timestampMs < cutoff }
    }

    private companion object { const val MAX_AGE_MS = 60_000L }
}
