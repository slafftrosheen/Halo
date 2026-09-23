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

        current.add(request)

        // Enforce bounds
        if (current.size > maxItems) {
            current.sortBy { it.queuedAt }
            while (current.size > maxItems) {
                current.removeAt(0)
            }
        }

        _pending.value = current
    }

    override suspend fun remove(requestId: AttentionRequestId) = mutex.withLock {
        _pending.value = _pending.value.filterNot { it.request.id == requestId }
    }

    override suspend fun releaseEligible(context: AttentionContext) = mutex.withLock {
        val now = context.currentTime
        val current = _pending.value

        val valid = current.filterNot { it.expiresAt != null && now.isAfter(it.expiresAt) }
        
        // Detailed eligibility resolution logic (e.g. check DND/Focus bounds) happens
        // in AttentionDeliveryCoordinator or a similar orchestrator, which reads `pending`
        // and can call `remove` as items are delivered. This method primarily handles expiry.
        if (valid.size != current.size) {
            _pending.value = valid
        }
    }
}
