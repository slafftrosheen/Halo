package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanRfcommCommandMapperTest {

    private val mapper = DefaultHeyCyanRfcommCommandMapper()

    @Test
    fun mapsGetBatteryToCommand0x13() {
        val result = mapper.map(HeyCyanCommand.GetBattery)
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        assertEquals(0x13, packet.command)
        assertTrue(packet.payload.isEmpty())
    }

    @Test
    fun mapsGetVersionToCommand0x10() {
        val result = mapper.map(HeyCyanCommand.GetVersion)
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        assertEquals(0x10, packet.command)
    }

    @Test
    fun mapsSetPhotoModeToCommand0x01Payload0x01() {
        val result = mapper.map(HeyCyanCommand.SetMode(HeyCyanMode.PHOTO))
        assertTrue(result.isSuccess)
        val packet = result.getOrThrow()
        assertEquals(0x01, packet.command)
        assertEquals(1, packet.payload.size)
        assertEquals(0x01.toByte(), packet.payload[0])
    }
}
