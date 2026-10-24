package com.halo.sdk.runtime.ink

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.InMemoryDeviceRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RemoteInkSessionOwnerTest {
    private val pin = "a".repeat(64)
    private val identity = DeviceIdentity("tablet", "Android", "My tablet")
    private val association = InkAssociation(InkEndpoint("tablet", width = 1000, height = 700, pressure = true), pin)
    private var now = 1000L
    private var enabled = true
    private val document = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2)), "stroke")))
    private fun owner(store: InkCheckpointStore = InkCheckpointStore { InkReceiveReceipt(InkReceiveStatus.SAVED, "next") }) =
        RemoteInkSessionOwner(InMemoryDeviceRegistry(), InkActivationPolicy { enabled }, store) { now }
    private suspend fun started(owner: RemoteInkSessionOwner): InkSession {
        assertTrue(owner.associate(identity, association))
        return owner.start("tablet", "phone", "phone", "interaction", "board", "document")!!
    }
    private fun frame(session: InkSession, sequence: Long = 1, checkpoint: Boolean = true,
        device: String = "tablet", interaction: String = "interaction", sent: Long = now) = InkEventCodec.encode(
        InkEventEnvelope(sourceDeviceId = device, sessionId = session.sessionId, interactionId = interaction,
            boardId = "board", documentId = "document", sequence = sequence, sentAtMs = sent,
            expectedRevision = "revision", payload = if (checkpoint) InkEventPayload.Checkpoint(document)
                else InkEventPayload.Preview(document.strokes.single())))

    @Test fun productionDefaultsRefuseAssociationAndStart() = runTest {
        val owner = RemoteInkSessionOwner(InMemoryDeviceRegistry())
        assertFalse(owner.associate(identity, association))
        assertNull(owner.start("tablet", "phone", "phone", "interaction", "board", "document"))
    }

    @Test fun wrongCertificateSourceAndInteractionNeverReachPersistence() = runTest {
        var writes = 0
        val owner = owner(InkCheckpointStore { writes++; error("must not write") })
        val session = started(owner)
        assertEquals(InkReceiveStatus.UNTRUSTED, owner.receive("b".repeat(64), frame(session)).status)
        assertEquals(InkReceiveStatus.UNTRUSTED, owner.receive(pin, frame(session, device = "other")).status)
        assertEquals(InkReceiveStatus.STALE, owner.receive(pin, frame(session, interaction = "other")).status)
        assertEquals(0, writes)
    }

    @Test fun previewReorderingAndCheckpointLossDoNotTurnPreviewIntoDurableInk() = runTest {
        var writes = 0
        val owner = owner(InkCheckpointStore { writes++; InkReceiveReceipt(InkReceiveStatus.SAVED, "next") })
        val session = started(owner)
        assertEquals(InkReceiveStatus.PREVIEW, owner.receive(pin, frame(session, 4, false)).status)
        assertEquals(InkReceiveStatus.STALE, owner.receive(pin, frame(session, 3, false)).status)
        assertEquals(0, writes)
        // A durable contact may arrive after a later ephemeral sample; it is still checked by CAS.
        assertEquals(InkReceiveStatus.SAVED, owner.receive(pin, frame(session, 2)).status)
        assertTrue(owner.previews.value.isEmpty())
        assertEquals(1, writes)
        owner.disconnect("tablet")
        assertEquals(InkReceiveStatus.STOPPED, owner.receive(pin, frame(session, 5)).status)
    }

    @Test fun gateRevocationAndClockRollbackClearEphemeralState() = runTest {
        val owner = owner(); val session = started(owner)
        owner.receive(pin, frame(session, checkpoint = false))
        enabled = false
        assertEquals(InkReceiveStatus.DISABLED, owner.receive(pin, frame(session, 2)).status)
        assertTrue(owner.sessions.value.isEmpty()); assertTrue(owner.previews.value.isEmpty())
        enabled = true
        val second = started(owner)
        now--
        assertEquals(InkReceiveStatus.STOPPED, owner.receive(pin, frame(second)).status)
    }

    @Test fun expirationAndFutureTimestampsCannotExtendAConsentSession() = runTest {
        val owner = owner(); val session = started(owner)
        assertEquals(InkReceiveStatus.STALE, owner.receive(pin,
            frame(session, sent = now + RemoteInkSessionOwner.FUTURE_SKEW_MS + 1)).status)
        now += RemoteInkSessionOwner.SESSION_LIFETIME_MS
        assertEquals(InkReceiveStatus.STOPPED, owner.receive(pin, frame(session)).status)
    }

    @Test fun previewFloodDoesNotConsumeCheckpointBudget() = runTest {
        val owner = owner(); val session = started(owner)
        repeat(RemoteInkSessionOwner.MAX_PREVIEWS_PER_SECOND) {
            assertEquals(InkReceiveStatus.PREVIEW, owner.receive(pin, frame(session, it + 1L, false)).status)
        }
        assertEquals(InkReceiveStatus.RATE_LIMITED, owner.receive(pin, frame(session, 121, false)).status)
        assertEquals(InkReceiveStatus.SAVED, owner.receive(pin, frame(session, 122)).status)
    }
    @Test fun abandonedPreviewExpiresWithoutPersistingAnIncompleteContact() = runTest {
        val owner = owner(InkCheckpointStore { error("Preview cannot persist") })
        val session = started(owner)
        owner.receive(pin, frame(session, checkpoint = false))
        assertFalse(owner.previews.value.isEmpty())
        now += RemoteInkSessionOwner.PREVIEW_LIFETIME_MS + 1
        owner.refresh()
        assertTrue(owner.previews.value.isEmpty())
        assertEquals(session, owner.sessions.value.single())
    }

    @Test fun conflictDoesNotAcknowledgeSaveOrErasePreview() = runTest {
        val owner = owner(InkCheckpointStore { InkReceiveReceipt(InkReceiveStatus.CONFLICT) })
        val session = started(owner)
        owner.receive(pin, frame(session, checkpoint = false))
        assertEquals(InkReceiveStatus.CONFLICT, owner.receive(pin, frame(session, 2)).status)
        assertFalse(owner.previews.value.isEmpty())
    }

    @Test fun stopSerializesWithInFlightCommitAndRejectsLaterWrites() = runTest {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val owner = owner(InkCheckpointStore {
            entered.complete(Unit); finish.await(); InkReceiveReceipt(InkReceiveStatus.SAVED, "next")
        })
        val session = started(owner)
        val write = async { owner.receive(pin, frame(session)) }
        entered.await()
        val stop = async { owner.stop(session.sessionId) }
        assertFalse(stop.isCompleted)
        finish.complete(Unit)
        assertEquals(InkReceiveStatus.SAVED, write.await().status); assertTrue(stop.await())
        assertEquals(InkReceiveStatus.STOPPED, owner.receive(pin, frame(session, 2)).status)
    }

    @Test fun forgetRevokesTrustAndRestartHasNoActiveConsent() = runTest {
        val registry = InMemoryDeviceRegistry()
        val owner = RemoteInkSessionOwner(registry, InkActivationPolicy { true }) { now }
        val session = started(owner)
        val recreated = RemoteInkSessionOwner(registry, InkActivationPolicy { true }) { now }
        assertEquals(InkReceiveStatus.STOPPED, recreated.receive(pin, frame(session)).status)
        owner.forget("tablet")
        assertNull(registry.getDevice("tablet")!!.inkAssociation)
        assertNull(owner.start("tablet", "phone", "phone", "interaction", "board", "document"))
    }

    @Test fun remoteSelectionRequiresSameInteractionAndFreshCanonicalSource() = runTest {
        var sourceCurrent = true
        val registry = InMemoryDeviceRegistry()
        val store = object : InkCheckpointStore {
            override suspend fun commit(request: InkCheckpointRequest) = InkReceiveReceipt(InkReceiveStatus.SAVED, "next")
            override suspend fun selectionIsCurrent(session: InkSession, expectedRevision: String,
                selection: InkSelectionContext) = sourceCurrent && expectedRevision == "revision"
        }
        val owner = RemoteInkSessionOwner(registry, InkActivationPolicy { true }, store) { now }
        val session = started(owner)
        val selection = InkSelectionContext("board", "document", InkSelectionBounds(0, 0, 100, 100),
            listOf("stroke"), "source", InkSelectionOrigin.BOX, now, now)
        val event = InkEventEnvelope(sourceDeviceId = "tablet", sessionId = session.sessionId,
            interactionId = "interaction", boardId = "board", documentId = "document", sequence = 1,
            sentAtMs = now, expectedRevision = "revision", payload = InkEventPayload.Selection(selection))
        assertEquals(InkReceiveStatus.PREVIEW, owner.receive(pin, InkEventCodec.encode(event)).status)
        assertEquals(selection, owner.selection(session.sessionId, "interaction"))
        assertNull(owner.selection(session.sessionId, "other-interaction"))
        sourceCurrent = false
        assertNull(owner.selection(session.sessionId, "interaction"))
        sourceCurrent = true
        assertNull(owner.selection(session.sessionId, "interaction")) // Stale anchor cannot resurrect.
        owner.receive(pin, InkEventCodec.encode(event.copy(sequence = 2)))
        registry.removeDevice("tablet")
        assertNull(owner.selection(session.sessionId, "interaction"))
    }
}
