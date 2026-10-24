package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class RemoteInkConnectionTest {
    private val document = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(5, 6, pressure = 700)), "pen")))
    private val offer = InkSessionOffer(InkSession("session", "tablet", "display", "processor", "interaction", "board", "document", 1000, 2000),
        InkEndpoint("tablet", pressure = true, width = 1400, height = 1000), "revision", document)

    @Test fun offerRoundTripPreservesOwnersGeometryAndEditableStrokeIdentity() {
        assertEquals(offer, InkSessionOfferCodec.decode(InkSessionOfferCodec.encode(offer)))
        val bytes = InkSessionOfferCodec.encode(offer)
        assertNull(InkSessionOfferCodec.decode(bytes + byteArrayOf(0)))
        assertNull(InkSessionOfferCodec.decode(bytes.copyOf(12)))
    }

    @Test fun checkpointRetryIsByteIdenticalAndRequiresExactReceiptBeforeNextEdit() {
        val publisher = RemoteInkPublisher(offer) { 1100 }
        val bytes = publisher.checkpoint(document)
        assertArrayEquals(bytes, publisher.retry())
        assertThrows(IllegalStateException::class.java) { publisher.checkpoint(document) }
        assertNull(publisher.preview(document.strokes.single()))
        assertEquals(InkReceiveStatus.SAVED, publisher.acknowledge(InkReceiptCodec.encode(
            InkWireReceipt(1, InkReceiveReceipt(InkReceiveStatus.SAVED, "next")))).status)
        assertNull(publisher.retry())
        val next = InkEventCodec.decode(publisher.checkpoint(InkDocument()))!!
        assertEquals("next", next.expectedRevision); assertEquals(2L, next.sequence)
        assertTrue((next.payload as InkEventPayload.Checkpoint).document.strokes.isEmpty())
    }

    @Test fun conflictNeverAdvancesRevisionOrReplaysAsAnotherCheckpoint() {
        val publisher = RemoteInkPublisher(offer) { 1100 }
        val bytes = publisher.checkpoint(document)
        publisher.acknowledge(InkReceiptCodec.encode(InkWireReceipt(1, InkReceiveReceipt(InkReceiveStatus.CONFLICT))))
        assertArrayEquals(bytes, publisher.retry())
        assertThrows(IllegalStateException::class.java) { publisher.checkpoint(InkDocument()) }
    }

    @Test fun wrongOrMalformedReceiptCannotConfirmPersistence() {
        val publisher = RemoteInkPublisher(offer) { 1100 }
        publisher.checkpoint(document)
        assertThrows(IllegalArgumentException::class.java) {
            publisher.acknowledge(InkReceiptCodec.encode(InkWireReceipt(2, InkReceiveReceipt(InkReceiveStatus.SAVED, "next"))))
        }
        assertNull(InkReceiptCodec.decode(InkReceiptCodec.encode(InkWireReceipt(1, InkReceiveReceipt(InkReceiveStatus.SAVED)))))
        assertNotNull(publisher.retry())
    }

    @Test fun pairingBundleContainsOnlyPublicMaterialAndDoesNotInventCapabilities() {
        val certificate = HeldCertificate.Builder().commonName("tablet").build().certificate
        val bundle = InkPairingBundle("My tablet", InkEndpoint(InkPairingBundle.deviceId(certificate), width = 1000, height = 700), certificate)
        val decoded = InkPairingBundleCodec.decode(InkPairingBundleCodec.encode(bundle))!!
        assertEquals(bundle.endpoint, decoded.endpoint)
        assertEquals(InkTlsIdentity.fingerprint(certificate), decoded.association().certificateSha256)
        assertFalse(decoded.endpoint.pressure); assertFalse(decoded.endpoint.eraser)
        assertNull(InkPairingBundleCodec.decode(InkPairingBundleCodec.encode(bundle) + byteArrayOf(0)))
        assertNull(InkPairingBundleCodec.decode(ByteArray(InkPairingBundleCodec.MAX_BYTES + 1)))
    }
}
