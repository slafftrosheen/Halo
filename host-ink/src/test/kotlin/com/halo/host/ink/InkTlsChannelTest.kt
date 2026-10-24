package com.halo.host.ink

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.halo.sdk.runtime.ink.InkActivationPolicy
import com.halo.sdk.runtime.ink.*
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.InMemoryDeviceRegistry
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.net.InetAddress
import java.security.KeyStore
import javax.net.ssl.SSLSocket

class InkTlsChannelTest {
    @Test fun savedReceiptConfirmsOnlyTheExactDocumentAcknowledgedByThePeer(): Unit = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        val now = System.currentTimeMillis()
        val offer = InkSessionOffer(InkSession("session", "tablet", "phone", "phone", "interaction", "board", "document", now, now + 60000),
            InkEndpoint("tablet", width = 1000, height = 700), "revision", InkDocument())
        val first = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2)), "first")))
        val latest = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(3, 4)), "latest")))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            InkTlsListener(context(server, client), address, 0, activation).use { listener ->
                val accept = async(Dispatchers.IO) { listener.accept() }
                val sourceChannel = InkTlsChannel.connect(context(client, server), address, listener.localPort, activation)
                accept.await().use { peer ->
                    val sender = InkSenderHost(sourceChannel, offer, scope)
                    try {
                        sender.checkpoint(first)
                        val firstEvent = InkEventCodec.decode(peer.read())!!
                        sender.checkpoint(latest)
                        assertNull(sender.confirmedDocumentSha256.value)
                        peer.write(InkReceiptCodec.encode(InkWireReceipt(firstEvent.sequence, InkReceiveReceipt(InkReceiveStatus.SAVED, "next"))))
                        val firstHash = InkDocumentIdentity.encodedSha256(InkCodec.encode(first))
                        withTimeout(2000) { sender.confirmedDocumentSha256.first { it == firstHash } }
                        val nextEvent = InkEventCodec.decode(peer.read())!!
                        assertEquals(latest, (nextEvent.payload as InkEventPayload.Checkpoint).document)
                        assertEquals(firstHash, sender.confirmedDocumentSha256.value)
                        peer.write(InkReceiptCodec.encode(InkWireReceipt(nextEvent.sequence, InkReceiveReceipt(InkReceiveStatus.SAVED, "final"))))
                        withTimeout(2000) { sender.confirmedDocumentSha256.first {
                            it == InkDocumentIdentity.encodedSha256(InkCodec.encode(latest))
                        } }
                    } finally { sender.close() }
                }
            }
        } finally { scope.cancel() }
    }
    @Test fun cancelledReceiverClearsItsPreviewEvenWhenAnotherCommitHoldsTheOwnerLock() = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val owner = RemoteInkSessionOwner(InMemoryDeviceRegistry(), InkActivationPolicy { true }, InkCheckpointStore {
            entered.complete(Unit); finish.await(); InkReceiveReceipt(InkReceiveStatus.SAVED, "next")
        })
        val pin = InkTlsIdentity.fingerprint(client.certificate)
        for (id in listOf("tablet", "tablet2")) {
            owner.associate(DeviceIdentity(id, "fixture", id), InkAssociation(InkEndpoint(id, width = 1000, height = 700), pin))
        }
        val first = owner.start("tablet", "phone", "phone", "interaction", "board", "document")!!
        val second = owner.start("tablet2", "phone", "phone", "interaction2", "board2", "document2")!!
        val endpoint = InkEndpoint("tablet", width = 1000, height = 700)
        val stroke = InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2)), "stroke")
        InkTlsListener(context(server, client), address, 0, activation).use { listener ->
            val receiving = async { RemoteInkConnection.receive(listener.accept(), owner,
                InkSessionOffer(first, endpoint, "revision", InkDocument())) }
            InkTlsChannel.connect(context(client, server), address, listener.localPort, activation).use { sender ->
                assertEquals(first, InkSessionOfferCodec.decode(sender.read())!!.session)
                val preview = InkEventEnvelope(sourceDeviceId = "tablet", sessionId = first.sessionId,
                    interactionId = first.interactionId, boardId = first.boardId, documentId = first.documentId,
                    sequence = 1, sentAtMs = System.currentTimeMillis(), expectedRevision = "revision", payload = InkEventPayload.Preview(stroke))
                sender.write(InkEventCodec.encode(preview))
                assertEquals(InkReceiveStatus.PREVIEW, InkReceiptCodec.decode(sender.read())!!.receipt.status)
                assertTrue(owner.previews.value.containsKey(first.sessionId))
                val blockedWrite = async(start = CoroutineStart.UNDISPATCHED) {
                    owner.receive(pin, InkEventCodec.encode(preview.copy(sourceDeviceId = "tablet2", sessionId = second.sessionId,
                        interactionId = second.interactionId, boardId = second.boardId, documentId = second.documentId,
                        payload = InkEventPayload.Checkpoint(InkDocument(listOf(stroke))))))
                }
                entered.await()
                receiving.cancel()
                yield()
                assertFalse(receiving.isCompleted)
                finish.complete(Unit)
                assertEquals(InkReceiveStatus.SAVED, blockedWrite.await().status)
                withTimeout(2000) { receiving.join() }
                assertFalse(owner.previews.value.containsKey(first.sessionId))
                assertFalse(owner.sessions.value.any { it.sessionId == first.sessionId })
                assertTrue(owner.sessions.value.any { it.sessionId == second.sessionId })
            }
        }
    }
    private val activation = InkActivationPolicy { true }
    private val address = InetAddress.getByName("127.0.0.1")
    private fun keys(certificate: HeldCertificate) = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry("ink", certificate.keyPair.private, "test-only".toCharArray(), arrayOf(certificate.certificate))
    }
    private fun context(local: HeldCertificate, trusted: HeldCertificate) = InkTlsIdentity.context(
        keys(local), "test-only".toCharArray(), setOf(InkTlsIdentity.fingerprint(trusted.certificate)))

    @Test fun mutuallyAuthenticatedLocalPeersExchangeBoundedFrames() = runBlocking {
        val server = HeldCertificate.Builder().commonName("ink-server").build()
        val client = HeldCertificate.Builder().commonName("ink-client").build()
        InkTlsListener(context(server, client), address, 0, activation).use { listener ->
            val accept = async(Dispatchers.IO) { listener.accept() }
            InkTlsChannel.connect(context(client, server), address, listener.localPort, activation).use { sender ->
                accept.await().use { receiver ->
                    assertEquals(InkTlsIdentity.fingerprint(client.certificate), receiver.peerCertificateSha256)
                    assertEquals(InkTlsIdentity.fingerprint(server.certificate), sender.peerCertificateSha256)
                    sender.write(byteArrayOf(1, 2, 3))
                    assertArrayEquals(byteArrayOf(1, 2, 3), receiver.read())
                    receiver.write(byteArrayOf(4)); assertArrayEquals(byteArrayOf(4), sender.read())
                }
            }
        }
    }

    @Test fun unassociatedServerFailsTlsBeforeApplicationData() = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        val other = HeldCertificate.Builder().commonName("unrelated").build()
        InkTlsListener(context(server, client), address, 0, activation).use { listener ->
            val accept = async(Dispatchers.IO) { runCatching { listener.accept().close() } }
            assertTrue(runCatching { InkTlsChannel.connect(context(client, other), address, listener.localPort, activation).close() }.isFailure)
            assertTrue(accept.await().isFailure)
        }
    }

    @Test fun unassociatedClientCannotReadAnApplicationFrame() = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        val other = HeldCertificate.Builder().commonName("unrelated").build()
        InkTlsListener(context(server, other), address, 0, activation).use { listener ->
            val accept = async(Dispatchers.IO) { runCatching { listener.accept().close() } }
            runCatching { InkTlsChannel.connect(context(client, server), address, listener.localPort, activation).use { it.read() } }
            assertTrue(accept.await().isFailure)
        }
    }

    @Test fun oversizedLengthIsRejectedWithoutAllocatingItsClaimedBody() = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        InkTlsListener(context(server, client), address, 0, activation).use { listener ->
            val accept = async(Dispatchers.IO) { listener.accept() }
            val socket = context(client, server).socketFactory.createSocket(address, listener.localPort) as SSLSocket
            socket.use {
                it.startHandshake()
                accept.await().use { receiver ->
                    DataOutputStream(it.outputStream).apply { writeInt(Int.MAX_VALUE); flush() }
                    assertTrue(runCatching { receiver.read() }.exceptionOrNull() is IllegalArgumentException)
                }
            }
        }
    }

    @Test fun cancellingReadClosesTheSocketPromptly() = runBlocking {
        val server = HeldCertificate.Builder().commonName("server").build()
        val client = HeldCertificate.Builder().commonName("client").build()
        InkTlsListener(context(server, client), address, 0, activation).use { listener ->
            val accept = async(Dispatchers.IO) { listener.accept() }
            InkTlsChannel.connect(context(client, server), address, listener.localPort, activation).use { sender ->
                accept.await().use { receiver ->
                    val pending = async(start = CoroutineStart.UNDISPATCHED) { receiver.read() }
                    pending.cancel()
                    withTimeout(2000) { pending.join() }
                    assertTrue(runCatching { receiver.write(byteArrayOf(1)) }.isFailure)
                }
            }
        }
    }
}

