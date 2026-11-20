package com.halo.app.capture

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** Per-socket interface binding leaves the phone's default network untouched. */
internal class LocalAddressSocketFactory(private val local: InetAddress) : SocketFactory() {
    override fun createSocket(): Socket = Socket().apply { bind(InetSocketAddress(local, 0)) }
    override fun createSocket(host: String, port: Int) = createSocket(InetAddress.getByName(host), port)
    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket().apply { connect(InetSocketAddress(host, port)) }
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) =
        createSocket(InetAddress.getByName(host), port, localHost, localPort)
    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket {
        require(localHost == local) { "Transfer socket must use the selected interface" }
        return Socket().apply { bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress(host, port)) }
    }
}

/** GET adapter for the existing bounded, hashing media downloader; OkHttp owns HTTP framing. */
internal class TransferHttpConnection(url: URL, private val client: OkHttpClient) : HttpURLConnection(url) {
    @Volatile private var call: okhttp3.Call? = null
    @Volatile private var response: okhttp3.Response? = null
    @Volatile private var closed = false

    @Synchronized override fun connect() {
        if (closed) throw IOException("Transfer connection closed")
        if (connected) return
        require(requestMethod == "GET") { "Transfer connection supports GET only" }
        val builder = Request.Builder().url(url).get()
        requestProperties.forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
        val pending = client.newBuilder().connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false).build().newCall(builder.build())
        call = pending
        if (closed) { pending.cancel(); throw IOException("Transfer connection closed") }
        val received = pending.execute()
        response = received
        if (closed) { received.close(); throw IOException("Transfer connection closed") }
        connected = true
    }

    override fun getResponseCode(): Int { connect(); return requireNotNull(response).code }
    override fun getInputStream(): InputStream { connect(); return requireNotNull(response?.body).byteStream() }
    override fun getHeaderField(name: String?): String? { connect(); return name?.let { response?.header(it) } }
    override fun getContentLengthLong(): Long { connect(); return response?.body?.contentLength() ?: -1L }
    override fun usingProxy() = false
    override fun disconnect() { closed = true; call?.cancel(); response?.close() }
}
