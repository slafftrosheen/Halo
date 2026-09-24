package com.halo.runtime.cognition.attention

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AttentionDeliveryTest {
    private fun request(key: String? = null) = AttentionRequest(AttentionRequestId("request"),
        AttentionSource.AgentCompletion, deliveryIntent = DeliveryIntent.ActionRequired("Done"), deduplicationKey = key)
    @Test fun `unsupported sink fails and expired direct request never delivers`() = runTest {
        val delivery = DefaultAttentionDeliveryCoordinator()
        val decision = AttentionDecision.DeliverNow(DeliveryChannel.PhoneSpeech, AttentionDecisionReason.DIRECT_USER_REQUEST)
        assertEquals(DeliveryOutcome.FAILED, delivery.deliver(request(), decision).outcome)
        assertEquals(DeliveryOutcome.EXPIRED, delivery.deliver(request().copy(expiresAt = Instant.EPOCH), decision).outcome)
        assertEquals(2, delivery.receipts.value.size)
    }
    @Test fun `deduplication replaces queued equivalent and dnd release calls sink`() = runTest {
        val queue = InMemoryAttentionQueue()
        var count = 0
        val sink = object : AttentionSink {
            override fun supports(channel: DeliveryChannel) = channel == DeliveryChannel.InApp
            override suspend fun deliver(request: AttentionRequest, channel: DeliveryChannel): AttentionSinkResult {
                count++
                return AttentionSinkResult(true)
            }
        }
        var context = AttentionContext(doNotDisturb = DoNotDisturbState.ACTIVE)
        val revision = MutableStateFlow(0)
        val provider = object : AttentionContextProvider {
            override fun snapshot() = context
            override val changes = revision.map { Unit }
        }
        val delivery = DefaultAttentionDeliveryCoordinator(queue, listOf(sink))
        val controller = AttentionController(provider, DefaultAttentionPolicyEngine(), queue, delivery, backgroundScope)
        assertEquals(DeliveryOutcome.QUEUED, controller.submit(request("same")).outcome)
        controller.submit(request("same").copy(id = AttentionRequestId("replacement")))
        assertEquals(1, queue.pending.value.size)
        assertEquals(0, count)
        context = context.copy(doNotDisturb = DoNotDisturbState.OFF)
        controller.release()
        assertEquals(1, count)
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(DeliveryOutcome.DELIVERED, delivery.receipts.value.last().outcome)
    }
    @Test fun `wearable request cannot use a phone-only sink`() = runTest {
        var called = false
        val phone = object : AttentionSink {
            override fun supports(channel: DeliveryChannel) = channel == DeliveryChannel.PhoneSpeech
            override suspend fun deliver(request: AttentionRequest, channel: DeliveryChannel): AttentionSinkResult {
                called = true
                return AttentionSinkResult(true)
            }
        }
        val delivery = DefaultAttentionDeliveryCoordinator(sinks = listOf(phone))
        val receipt = delivery.deliver(request(), AttentionDecision.DeliverNow(DeliveryChannel.WearableSpeech("glasses"), AttentionDecisionReason.DIRECT_USER_REQUEST))
        assertEquals(DeliveryOutcome.FAILED, receipt.outcome)
        assertFalse(called)
    }
}
