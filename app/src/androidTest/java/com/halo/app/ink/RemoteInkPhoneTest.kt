package com.halo.app.ink

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.host.android.ink.AndroidInkIdentity
import com.halo.host.android.ink.AndroidInkTabletConnection
import com.halo.host.ink.*
import com.halo.sdk.runtime.ink.*
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.InMemoryDeviceRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.security.KeyStore
import java.util.UUID

/** Fixture-only local TLS/key-store proof. No production activation, microphone, provider or LAN peer. */
@RunWith(AndroidJUnit4::class)
class RemoteInkPhoneTest {
    @Test fun productionGateStaysClosedAndProtectedIdentitiesAuthenticateOnLoopback() = runBlocking {
        assertFalse(InkActivationPolicy.QUALIFICATION_PENDING.enabled())
        assertTrue(runCatching { AndroidInkIdentity().certificate() }.isFailure)
        val enabled = InkActivationPolicy { true }
        val firstAlias = "halo.ink.test.${UUID.randomUUID()}"
        val secondAlias = "halo.ink.test.${UUID.randomUUID()}"
        val first = AndroidInkIdentity(enabled, firstAlias)
        val second = AndroidInkIdentity(enabled, secondAlias)
        try {
            val firstPin = first.fingerprint(); val secondPin = second.fingerprint()
            assertEquals(firstPin, first.fingerprint())
            assertNotEquals(firstPin, secondPin)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertNull(store.getKey(firstAlias, null).encoded)
            // Exercise TLS's prehashed signing operation before attempting the handshake.
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 2, 3))
            val signature = java.security.Signature.getInstance("NONEwithRSA").run {
                initSign(store.getKey(firstAlias, null) as java.security.PrivateKey)
                update(digest)
                sign()
            }
            assertTrue(java.security.Signature.getInstance("NONEwithRSA").run {
                initVerify(first.certificate().publicKey)
                update(digest)
                verify(signature)
            })
            val rawSignature = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding").run {
                init(javax.crypto.Cipher.ENCRYPT_MODE, store.getKey(firstAlias, null))
                doFinal(digest)
            }
            val restored = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding").run {
                init(javax.crypto.Cipher.DECRYPT_MODE, first.certificate().publicKey)
                doFinal(rawSignature)
            }
            assertArrayEquals(digest, restored.takeLast(digest.size).toByteArray())
            val firstContext = first.context(setOf(secondPin)); val secondContext = second.context(setOf(firstPin))
            val address = InetAddress.getByName("127.0.0.1")
            InkTlsListener(firstContext, address, 0, enabled).use { listener ->
                val accepted = async(Dispatchers.IO) { listener.accept() }
                InkTlsChannel.connect(secondContext, address, listener.localPort, enabled).use { sender ->
                    accepted.await().use { receiver ->
                        assertEquals(secondPin, receiver.peerCertificateSha256)
                        assertEquals(firstPin, sender.peerCertificateSha256)
                        sender.write(byteArrayOf(1, 2, 3))
                        assertArrayEquals(byteArrayOf(1, 2, 3), receiver.read())
                    }
                }
            }
            verifyLocalFallbackAfterNetworkStop(first, second, firstContext, enabled)
        } finally {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(firstAlias); store.deleteEntry(secondAlias)
        }
    }

    private suspend fun verifyLocalFallbackAfterNetworkStop(host: AndroidInkIdentity, source: AndroidInkIdentity,
        hostContext: javax.net.ssl.SSLContext, enabled: InkActivationPolicy) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sourceId = InkPairingBundle.deviceId(source.certificate())
        val endpoint = InkEndpoint(sourceId, width = 1000, height = 700)
        val persistedRemotely = MutableStateFlow<InkDocument?>(null)
        val owner = RemoteInkSessionOwner(InMemoryDeviceRegistry(), enabled, InkCheckpointStore {
            persistedRemotely.value = it.document; InkReceiveReceipt(InkReceiveStatus.SAVED, "next")
        })
        owner.associate(DeviceIdentity(sourceId, "Fixture", "Fixture tablet"), InkAssociation(endpoint, source.fingerprint()))
        val board = "fixture-${UUID.randomUUID()}"; val documentId = "fixture-${UUID.randomUUID()}"
        val session = owner.start(sourceId, "host", "host", "fixture-interaction", board, documentId)!!
        val initial = InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(1, 1)), "initial")))
        val sent = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(10, 20, pressure = 700)), "sent")))
        val local = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(30, 40)), "local-after-stop")))
        val key = InkLocalDraftStore.scopeKey(host.fingerprint(), board, documentId)
        val path = java.io.File(context.filesDir, "remote-ink-local/$key.ink").toPath()
        var connection: AndroidInkTabletConnection? = null
        try {
            val address = InetAddress.getByName("127.0.0.1")
            InkTlsListener(hostContext, address, 0, enabled).use { listener ->
                val receiving = fixtureScope.launch {
                    runCatching { RemoteInkConnection.receive(listener.accept(), owner,
                        InkSessionOffer(session, endpoint, "original", initial)) }
                }
                val tablet = AndroidInkTabletConnection.open(context,
                    InkConnectionInvitation(session.sessionId, "127.0.0.1", listener.localPort, host.fingerprint()),
                    setOf(host.fingerprint()), fixtureScope, enabled, source)
                connection = tablet
                assertTrue(tablet.edit(sent, true))
                withTimeout(10000) { tablet.sender.confirmedDocumentSha256.first { it == InkDocumentIdentity.encodedSha256(InkCodec.encode(sent)) } }
                assertEquals(sent, persistedRemotely.value)
                tablet.disconnect() // Same operation as the Android background lifecycle hook.
                assertTrue(tablet.edit(local, true))
                withTimeout(10000) { tablet.localState.first { it == InkLocalSaveState.SAVED } }
                assertEquals(local, withContext(Dispatchers.IO) { InkLocalDraftStore(path).load() })
                assertEquals(sent, persistedRemotely.value) // New local work never replays to the stopped channel.
                tablet.close(); withTimeout(10000) { tablet.awaitLocalClose(); receiving.join() }
            }
        } finally {
            connection?.close()
            withContext(NonCancellable) { withTimeout(10000) { connection?.awaitLocalClose() } }
            fixtureScope.cancel()
            withContext(Dispatchers.IO) { java.nio.file.Files.deleteIfExists(path) }
        }
    }
}
