package com.halo.runtime.cognition.attention

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class QueuedAttentionRequest(
    val request: AttentionRequest,
    val decisionReason: AttentionDecisionReason,
    val queuedAt: Instant,
    val expiresAt: Instant? = request.expiresAt,
    val releaseCondition: AttentionReleaseCondition = AttentionReleaseCondition.HALO_FOREGROUND,
)

interface AttentionQueue {
    val pending: StateFlow<List<QueuedAttentionRequest>>
    suspend fun enqueue(request: QueuedAttentionRequest)
    suspend fun remove(requestId: AttentionRequestId)
    suspend fun releaseEligible(context: AttentionContext)
}

class InMemoryAttentionQueue(
    private val maxItems: Int = 50,
) : AttentionQueue {

    private val _pending = MutableStateFlow<List<QueuedAttentionRequest>>(emptyList())
    override val pending: StateFlow<List<QueuedAttentionRequest>> = _pending.asStateFlow()
    private val mutex = Mutex()

    override suspend fun enqueue(request: QueuedAttentionRequest) = mutex.withLock {
        val current = _pending.value.toMutableList()

        // Deduplicate
        if (request.request.deduplicationKey != null) {
            current.removeAll { it.request.deduplicationKey == request.request.deduplicationKey }
        }

        current.removeAll { it.request.id == request.request.id }
        check(current.size < maxItems) { "Attention queue is full" }
        current.add(request)

        _pending.value = current
    }

    override suspend fun remove(requestId: AttentionRequestId) = mutex.withLock {
        _pending.value = _pending.value.filterNot { it.request.id == requestId }
    }

    // Expiry and release belong to AttentionController so every removal has a receipt.
    override suspend fun releaseEligible(context: AttentionContext) = Unit
}
