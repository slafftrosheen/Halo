package com.halo.host.ink

import com.halo.sdk.runtime.ink.InkEventCodec
import com.halo.sdk.runtime.ink.InkActivationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509ExtendedKeyManager
import kotlin.coroutines.resumeWithException

/**
 * Local-only mutually authenticated TLS. Trust is an explicit local certificate association;
 * there is no trust-on-first-use, discovery injection, custom cipher or vendor cloud.
 * A host supplies its protected KeyStore and an immutable snapshot of confirmed peer pins.
 */
object InkTlsIdentity {
    fun fingerprint(certificate: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    fun context(keys: KeyStore, keyPassword: CharArray?, trustedCertificateSha256: Set<String>, keyAlias: String? = null): SSLContext {
        require(trustedCertificateSha256.all { it.matches(Regex("[a-f0-9]{64}")) })
        val pins = trustedCertificateSha256.toSet()
        val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keys, keyPassword)
        }
        if (keyAlias != null) require(keys.isKeyEntry(keyAlias))
        val keyManagers = manager.keyManagers.map { delegate ->
            if (keyAlias == null || delegate !is X509KeyManager) delegate else object : X509ExtendedKeyManager() {
                override fun getClientAliases(type: String, issuers: Array<out java.security.Principal>?) =
                    delegate.getClientAliases(type, issuers)?.filter { it == keyAlias }?.toTypedArray()
                override fun getServerAliases(type: String, issuers: Array<out java.security.Principal>?) =
                    delegate.getServerAliases(type, issuers)?.filter { it == keyAlias }?.toTypedArray()
                override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?) =
                    types?.firstNotNullOfOrNull { getClientAliases(it, issuers)?.firstOrNull() }
                override fun chooseServerAlias(type: String, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?) =
                    getServerAliases(type, issuers)?.firstOrNull()
                override fun getCertificateChain(alias: String?) = if (alias == keyAlias) delegate.getCertificateChain(alias) else null
                override fun getPrivateKey(alias: String?) = if (alias == keyAlias) delegate.getPrivateKey(alias) else null
            }
        }.toTypedArray()
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = check(chain)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = check(chain)
            private fun check(chain: Array<X509Certificate>) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("Ink peer certificate missing")
                leaf.checkValidity()
                if (fingerprint(leaf) !in pins) throw CertificateException("Ink peer is not associated")
                // TLS verifies possession of the pinned leaf's private key. No CA fallback.
            }
        }
        return SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf<TrustManager>(trust), null) }
    }
}

/** One frame in flight, bounded allocation and socket deadlines. Own and close in host lifecycle. */
class InkTlsChannel private constructor(private val socket: SSLSocket, private val activation: InkActivationPolicy) : Closeable {
    val peerCertificateSha256: String = InkTlsIdentity.fingerprint(
        socket.session.peerCertificates.first() as X509Certificate)
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private val readLock = Any()
    private val writeLock = Any()

    suspend fun read(): ByteArray = cancellableIo(socket) {
        checkEnabled()
        synchronized(readLock) {
            val size = input.readInt()
            require(size in 1..InkEventCodec.MAX_FRAME_BYTES) { "Ink frame size rejected" }
            ByteArray(size).also(input::readFully)
        }
    }

    suspend fun write(frame: ByteArray) {
        checkEnabled()
        require(frame.size in 1..InkEventCodec.MAX_FRAME_BYTES)
        val frozen = frame.copyOf()
        cancellableIo(socket) { synchronized(writeLock) {
            output.writeInt(frozen.size); output.write(frozen); output.flush()
        } }
    }

    override fun close() { socket.close() }
    private fun checkEnabled() {
        if (!activation.enabled()) { close(); error("Ink activation pending") }
    }

    companion object {
        const val TIMEOUT_MS = 10_000
        private fun configure(socket: SSLSocket) {
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
            require(socket.enabledProtocols.isNotEmpty())
            socket.soTimeout = TIMEOUT_MS
            socket.tcpNoDelay = true
        }

        suspend fun connect(context: SSLContext, address: InetAddress, port: Int,
            activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING): InkTlsChannel {
            check(activation.enabled()) { "Ink activation pending" }
            requireLocal(address); require(port in 1..65535)
            val socket = context.socketFactory.createSocket() as SSLSocket
            return try {
                configure(socket)
                cancellableIo(socket) {
                    socket.connect(InetSocketAddress(address, port), TIMEOUT_MS)
                    socket.startHandshake()
                    check(activation.enabled()) { "Ink activation pending" }
                    InkTlsChannel(socket, activation)
                }
            } catch (error: Throwable) { socket.close(); throw error }
        }

        internal suspend fun accept(socket: SSLSocket, activation: InkActivationPolicy): InkTlsChannel = try {
            requireLocal(socket.inetAddress); configure(socket)
            socket.useClientMode = false; socket.needClientAuth = true
            cancellableIo(socket) {
                check(activation.enabled()) { "Ink activation pending" }
                socket.startHandshake()
                check(activation.enabled()) { "Ink activation pending" }
                InkTlsChannel(socket, activation)
            }
        } catch (error: Throwable) { socket.close(); throw error }
    }
}

/** No wildcard binding, automatic discovery or unbounded accept loop. */
class InkTlsListener(context: SSLContext, address: InetAddress, port: Int,
    private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING) : Closeable {
    private val socket: SSLServerSocket
    val localPort: Int get() = socket.localPort
    init {
        check(activation.enabled()) { "Ink activation pending" }
        requireLocal(address); require(port in 0..65535)
        socket = (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            needClientAuth = true
            soTimeout = InkTlsChannel.TIMEOUT_MS
            bind(InetSocketAddress(address, port), 1)
        }
    }
    suspend fun accept(): InkTlsChannel {
        check(activation.enabled()) { "Ink activation pending" }
        // Keep ownership outside the dispatcher handoff: prompt cancellation may discard
        // a successfully accepted result before withContext returns it to this coroutine.
        val accepted = java.util.concurrent.atomic.AtomicReference<SSLSocket?>()
        try {
            val peer = cancellableIo(socket) { (socket.accept() as SSLSocket).also(accepted::set) }
            val channel = InkTlsChannel.accept(peer, activation)
            accepted.set(null)
            return channel
        } finally { accepted.getAndSet(null)?.close() }
    }
    override fun close() { socket.close() }
}

private fun requireLocal(address: InetAddress) {
    require(!address.isAnyLocalAddress && !address.isMulticastAddress &&
        (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress))
}

/**
 * Run blocking host I/O off main. Cancellation closes the socket immediately, including a
 * blocked write (SO_TIMEOUT only bounds reads). The worker cannot publish after cancellation.
 */
private suspend fun <T> cancellableIo(resource: Closeable, operation: () -> T): T = try { withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { resource.close() } }
        try {
            val result = operation()
            continuation.resume(result) { _, value, _ ->
                if (value is Closeable) runCatching { value.close() }
            }
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
        }
    }
} } catch (cancelled: CancellationException) {
    runCatching { resource.close() }
    throw cancelled
}
