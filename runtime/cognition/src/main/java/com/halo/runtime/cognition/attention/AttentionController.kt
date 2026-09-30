package com.halo.runtime.cognition.attention

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

interface AttentionContextProvider {
    fun snapshot(): AttentionContext
    val changes: Flow<Unit>
}

/** One policy/delivery composition; release is event-driven with a single nearest-expiry timer. */
class AttentionController(
    private val contextProvider: AttentionContextProvider,
    private val policy: AttentionPolicyEngine,
    private val queue: AttentionQueue,
    private val delivery: AttentionDeliveryCoordinator,
    private val scope: CoroutineScope,
    private val preferences: () -> AttentionPreferences = { AttentionPreferences() },
) {
    private val mutex = Mutex()
    private var expiryJob: Job? = null
    init {
        scope.launch { contextProvider.changes.collect { release() } }
        scope.launch {
            var restoredIds: Set<String> = emptySet()
            queue.pending.collect { pending ->
                val ids = pending.map { it.request.id.value }.toSet()
                if(ids != restoredIds) { restoredIds = ids; scope.launch { release() } }
                expiryJob?.cancel()
                pending.mapNotNull { it.expiresAt }.minOrNull()?.let { next ->
                    expiryJob = scope.launch {
                        delay(Duration.between(Instant.now(), next).toMillis().coerceAtLeast(1))
                        scope.launch { release() }
                    }
                }
            }
        }
    }
    suspend fun submit(request: AttentionRequest): DeliveryReceipt = mutex.withLock {
        delivery.deliver(request, policy.evaluate(request, contextProvider.snapshot(), preferences()))
    }
    suspend fun release() = mutex.withLock {
        val context = contextProvider.snapshot()
        queue.pending.value.toList().forEach { item ->
            val expired = item.expiresAt?.let { !context.currentTime.isBefore(it) } == true
            val eligible = when (item.releaseCondition) {
                AttentionReleaseCondition.DND_ENDED -> context.doNotDisturb == DoNotDisturbState.OFF && context.userMode != HaloUserMode.SLEEP
                AttentionReleaseCondition.HALO_FOREGROUND -> context.foregroundState == ForegroundState.HALO_FOREGROUND
                AttentionReleaseCondition.INTERACTION_ENDED -> !context.activeInteraction && !context.activeSpeechSession
            }
            if (expired || eligible) {
                val receipt = delivery.deliver(item.request, policy.evaluate(item.request, context, preferences()))
                if (receipt.outcome != DeliveryOutcome.QUEUED) queue.remove(item.request.id)
            }
        }
    }
}
