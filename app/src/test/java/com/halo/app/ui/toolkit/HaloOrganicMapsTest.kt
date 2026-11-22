package com.halo.app.ui.toolkit

import com.halo.app.data.toolkit.HaloMapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HaloOrganicMapsTest {
    private val start = OrganicMapsPoint(HaloMapCoordinate(56.95, 24.1), "Home & work")
    private val end = OrganicMapsPoint(HaloMapCoordinate(56.9496, 24.1052), "Café #2")

    @Test
    fun routeUsesTheReleasedParserOrderAndEncodesPointNames() {
        val link = HaloOrganicMaps.route(start, end, OrganicMapsTravelMode.BIKE)

        assertEquals("om", link.scheme)
        assertEquals("route", link.authority)
        val queryKeys = link.encodedQuery.orEmpty().split('&').map { it.substringBefore('=') }
        assertEquals(listOf("sll", "saddr", "dll", "daddr", "type"), queryKeys)
        assertEquals("56.95,24.1", link.getQueryParameter("sll"))
        assertEquals("56.9496,24.1052", link.getQueryParameter("dll"))
        assertEquals(start.label, link.getQueryParameter("saddr"))
        assertEquals(end.label, link.getQueryParameter("daddr"))
        assertEquals("bicycle", link.getQueryParameter("type"))
    }

    @Test
    fun destinationOnlyUsesAMapPinWithNoInventedStartOrSearchLocation() {
        val link = HaloOrganicMaps.destination(end)

        assertEquals("map", link.authority)
        assertEquals("1", link.getQueryParameter("v"))
        assertEquals("56.9496,24.1052", link.getQueryParameter("ll"))
        assertEquals(end.label, link.getQueryParameter("n"))
        assertNull(link.getQueryParameter("sll"))
        assertNull(HaloOrganicMaps.search("аптека", "ru-RU").getQueryParameter("cll"))
    }
}
