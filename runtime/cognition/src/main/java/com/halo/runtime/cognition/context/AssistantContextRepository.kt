package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.halo.sdk.runtime.ScreenContextSnapshot
import java.util.concurrent.ConcurrentHashMap

/** Process-local, transient assistant context. Nothing here is persisted as memory. */
class AssistantContextRepository(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    eventBus: EventBus? = null,
    scope: CoroutineScope? = null,
) {
    private val screens = ConcurrentHashMap<String, ScreenContextSnapshot>()
    private val images = ConcurrentHashMap<String, ImageContext>()

    init {
        if (eventBus != null && scope != null) {
            scope.launch {
                eventBus.observe(InteractionTurnChanged::class.java).collect { event ->
                    when (val state = event.state) {
                        is InteractionTurnState.Completed -> clear(state.interactionId)
                        is InteractionTurnState.Failed -> clear(state.interactionId)
                        is InteractionTurnState.Cancelled -> clear(state.interactionId)
                        is InteractionTurnState.Interrupted -> clear(state.interactionId)
                        else -> {}
                    }
                }
            }
        }
    }

    fun put(interactionId: String, snapshot: ScreenContextSnapshot) {
        screens[interactionId] = snapshot
        evictExpired()
    }

    fun screenFor(interactionId: String): ScreenContextSnapshot? =
        screens[interactionId]?.takeIf { nowMs() - it.capturedAtMs <= ttlMs }

    fun putImage(interactionId: String, image: ImageContext) {
        images[interactionId] = image
        evictExpired()
    }

    fun imageFor(interactionId: String): ImageContext? =
        images[interactionId]?.takeIf { it.freshness == com.halo.sdk.runtime.VisualFreshness.REFERENCE || nowMs() - it.timestampMs <= ttlMs }

    fun clear(interactionId: String) {
        screens.remove(interactionId)
        images.remove(interactionId)
    }

    private fun evictExpired() {
        val cutoff = nowMs() - ttlMs
        screens.entries.removeIf { it.value.capturedAtMs < cutoff }
        images.entries.removeIf { it.value.freshness != com.halo.sdk.runtime.VisualFreshness.REFERENCE && it.value.timestampMs < cutoff }
    }

    private companion object { const val DEFAULT_TTL_MS = 60_000L }
}
