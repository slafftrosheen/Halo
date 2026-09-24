package com.halo.runtime.cognition.attention

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

interface AttentionSink {
    fun supports(channel: DeliveryChannel): Boolean
    /** Return only after the channel supplies delivery evidence. */
    suspend fun deliver(request: AttentionRequest, channel: DeliveryChannel): AttentionSinkResult
}
data class AttentionSinkResult(val delivered: Boolean, val reason: String? = null)

interface AttentionDeliveryCoordinator {
    suspend fun deliver(request: AttentionRequest, decision: AttentionDecision): DeliveryReceipt
}

class DefaultAttentionDeliveryCoordinator(
    private val queue: AttentionQueue = InMemoryAttentionQueue(),
    private val sinks: List<AttentionSink> = emptyList(),
    private val silentSurface: suspend (AttentionRequest, SilentSurface) -> Boolean = { _, _ -> false },
) : AttentionDeliveryCoordinator {
    private val _receipts = MutableStateFlow<List<DeliveryReceipt>>(emptyList())
    val receipts = _receipts.asStateFlow()

    override suspend fun deliver(request: AttentionRequest, decision: AttentionDecision): DeliveryReceipt {
        val now = Instant.now()
        val receipt = if (request.expiresAt?.let { !now.isBefore(it) } == true) {
            queue.remove(request.id)
            DeliveryReceipt(request.id, DeliveryOutcome.EXPIRED, reason = "Expired before delivery")
        } else try {
            when (decision) {
                is AttentionDecision.DeliverNow -> {
                    val sink = sinks.firstOrNull { it.supports(decision.channel) }
                    val result = sink?.deliver(request, decision.channel)
                        ?: AttentionSinkResult(false, "Unsupported delivery channel")
                    DeliveryReceipt(request.id, if (result.delivered) DeliveryOutcome.DELIVERED else DeliveryOutcome.FAILED,
                        decision.channel, Instant.now().takeIf { result.delivered }, result.reason)
                }
                is AttentionDecision.Queue -> {
                    queue.enqueue(QueuedAttentionRequest(request, decision.reason, now, releaseCondition = decision.until))
                    DeliveryReceipt(request.id, DeliveryOutcome.QUEUED, reason = decision.reason.name)
                }
                is AttentionDecision.SurfaceSilently -> {
                    val surfaced = silentSurface(request, decision.surface)
                    DeliveryReceipt(request.id, if (surfaced) DeliveryOutcome.SURFACED_SILENTLY else DeliveryOutcome.FAILED,
                        reason = if (surfaced) decision.reason.name else "Unsupported silent surface")
                }
                is AttentionDecision.Suppress -> DeliveryReceipt(request.id,
                    if (decision.reason == AttentionDecisionReason.EXPIRED) DeliveryOutcome.EXPIRED else DeliveryOutcome.SUPPRESSED,
                    reason = decision.reason.name)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { DeliveryReceipt(request.id, DeliveryOutcome.FAILED, reason = "Delivery adapter failed") }
        _receipts.update { (it + receipt).takeLast(200) }
        return receipt
    }
}
