package com.halo.host.android.ink

import com.halo.host.ink.CoachInkPage
import com.halo.host.ink.CoachInkWire
import com.halo.host.ink.InkConnectionInvitation
import com.halo.host.ink.InkPairingBundle
import com.halo.host.ink.InkTlsChannel
import com.halo.sdk.runtime.ink.InkActivationPolicy
import com.halo.sdk.runtime.ink.InkDocument
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The tablet retains only the current page and one bounded, explicitly submitted scratch checkpoint. */
class AndroidCoachInkConnection private constructor(
    private val channel: InkTlsChannel,
    private val client: CoachInkWire.Client,
    initial: CoachInkPage,
    scope: CoroutineScope,
    private val authorized: suspend () -> Boolean,
) : Closeable {
    private data class Draft(val page: CoachInkPage, val document: InkDocument)
    private val drafts = Channel<Draft>(1)
    private val closed = AtomicBoolean(false)
    private val mutablePage = MutableStateFlow<CoachInkPage?>(initial)
    val page = mutablePage.asStateFlow()
    private val mutableLastSend = MutableStateFlow<Boolean?>(null)
    val lastSend = mutableLastSend.asStateFlow()
    private val job = scope.launch {
        try {
            withTimeout(CoachInkWire.SESSION_TIMEOUT_MS) {
                while (!closed.get() && authorized()) {
                    val draft = drafts.tryReceive().getOrNull()
                    val reply = if (draft == null) client.read() else client.send(draft.page, draft.document)
                    if (closed.get() || !authorized()) break
                    mutablePage.value = reply.page
                    if (draft != null) mutableLastSend.value = reply.acceptedWriting
                    delay(300)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A lost or invalid connection removes the private page. */ }
        finally {
            mutablePage.value = null
            mutableLastSend.value = null
            drafts.cancel()
            channel.close()
        }
    }

    fun send(page: CoachInkPage, document: InkDocument): Boolean {
        if (closed.get() || mutablePage.value != page || !page.content.acceptsWriting || document.strokes.isEmpty() ||
            document.strokes.size > 200 || document.strokes.sumOf { it.points.size } > 8_000) return false
        mutableLastSend.value = null
        return drafts.trySend(Draft(page, document)).isSuccess
    }

    override fun close() {
        closed.set(true)
        job.cancel()
        runCatching(channel::close)
        mutablePage.value = null
        drafts.cancel()
    }

    companion object {
        suspend fun open(invitation: InkConnectionInvitation, scope: CoroutineScope,
            activation: InkActivationPolicy, authorized: suspend () -> Boolean): AndroidCoachInkConnection {
            check(activation.enabled() && authorized())
            val identity = AndroidInkIdentity(activation)
            val (tls, deviceId) = withContext(Dispatchers.IO) {
                identity.context(setOf(invitation.hostCertificateSha256)) to InkPairingBundle.deviceId(identity.certificate())
            }
            val channel = InkTlsChannel.connect(tls, InetAddress.getByName(invitation.address), invitation.port, activation)
            try {
                check(authorized() && channel.peerCertificateSha256 == invitation.hostCertificateSha256)
                val client = CoachInkWire.Client(channel, invitation.sessionId, deviceId, activation)
                val initial = client.read().page
                check(authorized())
                return AndroidCoachInkConnection(channel, client, initial, scope, authorized)
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }
    }
}
