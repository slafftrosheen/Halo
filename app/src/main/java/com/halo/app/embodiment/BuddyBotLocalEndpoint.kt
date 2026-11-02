package com.halo.app.embodiment

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy

/** Paired robot traffic stays on a literal local device address, never an Internet/provider escape. */
internal object BuddyBotLocalEndpoint {
    const val MESSAGE="Use BuddyBot’s local IP address."
    fun acceptsWebSocket(value: String): Boolean = runCatching {
        require(value.substringBefore(":").lowercase() in setOf("ws","wss"))
        val url=Request.Builder().url(value).build().url
        url.username.isEmpty() && url.password.isEmpty() && local(url.host)!=null
    }.getOrDefault(false)
    private fun local(host: String): InetAddress? = runCatching {
        val address=if(host.contains(':')) {
            require(host.matches(Regex("[0-9a-fA-F:]+")))
            InetAddress.getByName(host) // A colon-only IPv6 literal cannot invoke hostname DNS.
        } else {
            require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
            val parts=host.split('.').map {it.toInt().also {n -> require(n in 0..255)}}
            InetAddress.getByAddress(parts.map {it.toByte()}.toByteArray())
        }
        val ula=address.address.size==16 && (address.address[0].toInt() and 0xfe)==0xfc
        address.takeIf {!it.isAnyLocalAddress && !it.isMulticastAddress && (it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || ula)}
    }.getOrNull()
    fun client(base: OkHttpClient,onRejected: ()->Unit = {}): OkHttpClient = base.newBuilder()
        .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .dns(object: okhttp3.Dns {override fun lookup(hostname: String): List<InetAddress> = listOf(local(hostname) ?: throw java.net.UnknownHostException(MESSAGE))})
        .addInterceptor {chain ->
            val url=chain.request().url
            if(url.username.isNotEmpty() || url.password.isNotEmpty() || local(url.host)==null) {
                onRejected();throw IOException(MESSAGE)
            }
            chain.proceed(chain.request())
        }.build()
}
