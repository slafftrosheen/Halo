package com.halo.app.ink

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.halo.host.android.ink.AndroidInkIdentity
import com.halo.host.ink.CoachInkContent
import com.halo.host.ink.CoachInkPage
import com.halo.host.ink.CoachInkWire
import com.halo.host.ink.InkTlsChannel
import com.halo.host.ink.InkTlsListener
import com.halo.sdk.runtime.ink.InkActivationPolicy
import com.halo.sdk.runtime.ink.InkDocument
import java.io.Closeable
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal enum class CoachInkHostState { WAITING, CONNECTED, ENDED }
internal data class CoachInkWriting(val content: CoachInkContent, val document: InkDocument)

/** A separate ephemeral session. Pairing is delegated to the existing Ink owner; Canvas is never involved. */
internal class CoachInkMirrorHost private constructor(
    private val listener: InkTlsListener,
    val address: String,
    val code: String,
    val name: String,
    private val pin: String,
    private val deviceId: String,
    initial: CoachInkContent,
    scope: CoroutineScope,
    private val authorized: suspend () -> Boolean,
) : Closeable {
    val port = listener.localPort
    private val session = CoachInkWire.sessionId(code)
    private val channel = AtomicReference<InkTlsChannel?>()
    private val lock = Any()
    private var page = CoachInkPage(1, initial)
    private val mutableState = MutableStateFlow(CoachInkHostState.WAITING)
    val state = mutableState.asStateFlow()
    private val mutableWriting = MutableStateFlow<CoachInkWriting?>(null)
    val writing = mutableWriting.asStateFlow()

    private val job = scope.launch {
        try {
            val client = listener.accept()
            channel.set(client)
            listener.close()
            client.use {
                check(it.peerCertificateSha256 == pin && authorized())
                withTimeout(CoachInkWire.SESSION_TIMEOUT_MS) {
                    CoachInkWire.host(it, session, deviceId, authorized, readPage = {
                        synchronized(lock) {
                            check(mutableState.value != CoachInkHostState.ENDED)
                            mutableState.value = CoachInkHostState.CONNECTED
                            page
                        }
                    }, acceptWriting = { expected, document ->
                        synchronized(lock) {
                            (page == expected).also { current ->
                                if (current) mutableWriting.value = CoachInkWriting(page.content, document)
                            }
                        }
                    })
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* State is observable; never log Coach content or peer data. */ }
        finally { finish() }
    }

    fun update(content: CoachInkContent) = synchronized(lock) {
        if (mutableState.value != CoachInkHostState.ENDED && content != page.content) {
            page = CoachInkPage(page.version + 1, content)
            mutableWriting.value = null
        }
    }

    override fun close() { job.cancel(); finish() }

    private fun finish() {
        synchronized(lock) {
            mutableState.value = CoachInkHostState.ENDED
            mutableWriting.value = null
        }
        channel.getAndSet(null)?.let { runCatching(it::close) }
        runCatching(listener::close)
    }

    companion object {
        suspend fun start(context: Context, ink: CrossDeviceInkController, deviceId: String, name: String, pin: String,
            content: CoachInkContent, scope: CoroutineScope, allowsReview: () -> Boolean): CoachInkMirrorHost {
            val activation = InkActivationPolicy { ink.enabled && allowsReview() }
            suspend fun authorized() = activation.enabled() && ink.devices().any {
                it.identity.deviceId == deviceId && it.inkAssociation?.certificateSha256 == pin
            }
            check(authorized())
            var socket: InkTlsListener? = null
            try {
                val address = withContext(Dispatchers.IO) {
                    val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
                    val network = requireNotNull(manager.activeNetwork)
                    val capabilities = requireNotNull(manager.getNetworkCapabilities(network))
                    check(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    val local = requireNotNull(manager.getLinkProperties(network)).linkAddresses.map { it.address }
                        .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress || it.isLinkLocalAddress }
                    val tls = AndroidInkIdentity(activation).context(setOf(pin))
                    socket = InkTlsListener(tls, local, 0, activation, CoachInkWire.ACCEPT_TIMEOUT_MS)
                    requireNotNull(local.hostAddress)
                }
                check(authorized())
                val code = SecureRandom().nextInt(100_000_000).toString().padStart(CoachInkWire.CODE_LENGTH, '0')
                return CoachInkMirrorHost(requireNotNull(socket), address, code, name, pin, deviceId, content, scope, ::authorized)
            } catch (error: Throwable) {
                socket?.let { runCatching(it::close) }
                throw error
            }
        }
    }
}
