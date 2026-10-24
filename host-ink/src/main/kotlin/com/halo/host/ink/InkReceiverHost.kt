package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext

enum class InkReceiverState { WAITING, AUTHENTICATING, CONNECTED, DISCONNECTED }

/**
 * A single explicitly approved input session, no discovery or auto reconnect. A restarted
 * host needs a fresh explicit session. Stop closes both the listener and any active socket.
 */
class InkReceiverHost(
    context: SSLContext,
    address: InetAddress,
    port: Int,
    private val owner: RemoteInkSessionOwner,
    private val sessionId: String,
    private val offer: suspend (InkSession) -> InkSessionOffer?,
    scope: CoroutineScope,
    private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
) : Closeable {
    private val mutableState = MutableStateFlow(InkReceiverState.WAITING)
    val state: StateFlow<InkReceiverState> = mutableState.asStateFlow()
    private val listener = InkTlsListener(context, address, port, activation)
    val localPort: Int get() = listener.localPort
    private val channel = AtomicReference<InkTlsChannel?>()
    private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            // Invalid peers get no session offer. Failed authentication closes this attempt;
            // a person may retry explicitly rather than leaving a forever-listening port.
            mutableState.value = InkReceiverState.AUTHENTICATING
            val accepted = listener.accept()
            channel.set(accepted)
            accepted.use {
                val session = requireNotNull(owner.authenticatedSession(sessionId, accepted.peerCertificateSha256))
                val snapshot = requireNotNull(offer(session))
                check(activation.enabled())
                mutableState.value = InkReceiverState.CONNECTED
                RemoteInkConnection.receive(accepted, owner, snapshot)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Content-free observed state below; never log ink or certificates. */ }
        finally {
            listener.close(); channel.getAndSet(null)?.close()
            withContext(NonCancellable) { owner.stop(sessionId) }
            mutableState.value = InkReceiverState.DISCONNECTED
        }
    }
    override fun close() { channel.getAndSet(null)?.close(); listener.close(); job.cancel() }
}
