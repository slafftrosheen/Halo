package com.halo.app.embodiment

import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.net.*
import javax.net.SocketFactory

class BuddyBotLocalEndpointTest {
    @Test fun onlyLiteralPrivateDeviceAddressesAreAllowedIncludingIpv6Ula() {
        listOf("ws://192.168.4.1/ws","wss://10.1.2.3/ws","ws://172.16.0.1/ws","ws://127.0.0.1/ws","ws://169.254.1.1/ws","ws://[::1]/ws","wss://[fd12::1]/ws","ws://[fe80::1]/ws").forEach {
            assertTrue(it,BuddyBotLocalEndpoint.acceptsWebSocket(it))
        }
        listOf("ws://example.org/ws","wss://1.1.1.1/ws","ws://0.0.0.0/ws","ws://224.0.0.1/ws","ws://[::]/ws","ws://[ff02::1]/ws","wss://[2001:4860:4860::8888]/ws","ws://user:secret@192.168.4.1/ws","https://192.168.4.1/ws").forEach {
            assertFalse(it,BuddyBotLocalEndpoint.acceptsWebSocket(it))
        }
    }
    @Test fun publicOrHostnameWebSocketMakesZeroDnsAndSocketCallsAndReportsActionableError() {
        var dns=0;var sockets=0;var rejected=0
        val base=OkHttpClient.Builder().eventListener(object: EventListener() {
            override fun dnsStart(call: Call,domainName: String) {dns++}
            override fun connectStart(call: Call,inetSocketAddress: InetSocketAddress,proxy: Proxy) {sockets++}
        }).socketFactory(object: SocketFactory() {
            private fun blocked(): Socket=throw IOException("No test may reach a socket")
            override fun createSocket()=blocked()
            override fun createSocket(host: String,port: Int)=blocked()
            override fun createSocket(host: String,port: Int,local: InetAddress,localPort: Int)=blocked()
            override fun createSocket(host: InetAddress,port: Int)=blocked()
            override fun createSocket(host: InetAddress,port: Int,local: InetAddress,localPort: Int)=blocked()
        }).build()
        val client=BuddyBotLocalEndpoint.client(base) {rejected++}
        try {
            for(url in listOf("ws://example.org/ws","wss://1.1.1.1/ws","ws://[2001:4860:4860::8888]/ws")) {
                val done=CountDownLatch(1)
                client.newWebSocket(Request.Builder().url(url).build(),object: WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) {
                        assertEquals(BuddyBotLocalEndpoint.MESSAGE,t.message);done.countDown()
                    }
                })
                assertTrue(done.await(5,TimeUnit.SECONDS))
            }
            assertEquals(3,rejected);assertEquals(0,dns);assertEquals(0,sockets)
        } finally {client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
    }
    @Test fun aLocalDeviceRedirectCannotEscapeToPublicInternetOrUseAProxy() {
        val server=MockWebServer();server.start()
        val base=OkHttpClient.Builder().proxy(Proxy(Proxy.Type.HTTP,InetSocketAddress("127.0.0.1",1))).build()
        val client=BuddyBotLocalEndpoint.client(base)
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://example.org/not-a-device"))
            client.newCall(Request.Builder().url(server.url("/ws")).build()).execute().use {assertEquals(302,it.code)}
            assertEquals(1,server.requestCount);assertEquals(Proxy.NO_PROXY,client.proxy)
            assertFalse(client.followRedirects);assertFalse(client.followSslRedirects)
        } finally {server.shutdown();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
    }
}
