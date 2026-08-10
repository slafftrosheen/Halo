package com.halo.drivers.heycyan.wifi

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class HeyCyanWifiAddressResolverTest {

    @Test
    fun resolvesAddressFromConnectionWhenAvailable() = runTest {
        val fakeConnection = FakeHeyCyanWifiConnection()
        val expectedAddress = InetAddress.getByName("192.168.43.1")
        fakeConnection.addressToReturn = expectedAddress

        val resolver = HeyCyanWifiAddressResolver(fakeConnection)
        val result = resolver.resolve()

        assertTrue(result.isSuccess)
        val uri = result.getOrThrow()
        assertEquals("http://192.168.43.1", uri.toString())
    }
}
