package com.halo.host.android.ink

import android.content.Context
import com.halo.host.ink.*
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Android source host: preserve local completed edits before handing snapshots to the TLS actor. */
class AndroidInkTabletConnection private constructor(
    val sender: InkSenderHost,
    val initial: InkDocument,
    private val store: InkLocalDraftStore,
    scope: CoroutineScope,
) : java.io.Closeable {
    val offer get() = sender.offer
    private val closed = AtomicBoolean(false)
    private val disconnected = AtomicBoolean(false)
    private val writer = InkLocalDraftWriter(scope, write = { store.save(it) }, onPersisted = {
        if (!closed.get() && !disconnected.get()) sender.checkpoint(it)
    })
    val localState = writer.state
    fun edit(document: InkDocument, share: Boolean): Boolean {
        if (closed.get()) return false
        return writer.edit(document, share)
    }
    fun disconnect() { disconnected.set(true); sender.close() }
    override fun close() {
        closed.set(true); disconnect(); writer.close()
        // Finish at most one queued local checkpoint on the process-owned I/O scope.
        // Closing never transmits queued input or holds an Activity/Compose callback.
    }
    suspend fun awaitLocalClose() { writer.awaitClosed() }
    companion object {
        suspend fun open(context: Context, invitation: InkConnectionInvitation, trustedHostPins: Set<String>,
            scope: CoroutineScope, activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
            identity: AndroidInkIdentity = AndroidInkIdentity(activation)): AndroidInkTabletConnection {
            check(activation.enabled())
            require(invitation.hostCertificateSha256 in trustedHostPins)
            val tls = withContext(Dispatchers.IO) { identity.context(setOf(invitation.hostCertificateSha256)) }
            val channel = InkTlsChannel.connect(tls, java.net.InetAddress.getByName(invitation.address), invitation.port, activation)
            try {
                val offer = requireNotNull(InkSessionOfferCodec.decode(channel.read()))
                require(channel.peerCertificateSha256 == invitation.hostCertificateSha256 && offer.session.sessionId == invitation.sessionId)
                require(offer.session.inputOwner == withContext(Dispatchers.IO) { InkPairingBundle.deviceId(identity.certificate()) })
                val key = InkLocalDraftStore.scopeKey(channel.peerCertificateSha256, offer.session.boardId, offer.session.documentId)
                val store = InkLocalDraftStore(java.io.File(context.applicationContext.filesDir, "remote-ink-local/$key.ink").toPath())
                val initial = withContext(Dispatchers.IO) { store.load() } ?: offer.document
                return AndroidInkTabletConnection(InkSenderHost(channel, offer, scope), initial, store, scope)
            } catch (error: Throwable) { channel.close(); throw error }
        }
    }
}
