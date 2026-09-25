package com.halo.drivers.heycyan.wifi

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HeyCyanIpDiscoveryTest {

    @Test
    fun `announced glasses IP is verified before candidate probing`() = runTest {
        val openedHosts = mutableListOf<String>()
        val discovery = HeyCyanIpDiscovery(
            perIpTimeoutMillis = 100L,
            totalTimeoutMillis = 5_000L,
            ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler),
            connectionFactory = { url ->
                openedHosts += url.host
                FakeHttpConnection(url, if (url.host == "192.168.49.207") 200 else 404)
            },
        )

        val result = discovery.discover("192.168.49.207")

        assertEquals("192.168.49.207", result)
        assertEquals(listOf("192.168.49.207"), openedHosts)
    }

    private class FakeHttpConnection(
        url: URL,
        private val code: Int,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
    }
}
