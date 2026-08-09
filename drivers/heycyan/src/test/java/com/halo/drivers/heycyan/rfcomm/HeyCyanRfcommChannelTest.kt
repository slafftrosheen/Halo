package com.halo.drivers.heycyan.rfcomm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanRfcommChannelTest {
    @Test
    fun candidateChannelsKeepReferenceTransportSeparateFromProtocolMeaning() {
        assertEquals(
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),
            HeyCyanRfcommChannel.PRIMARY.uuid
        )
        assertTrue(HeyCyanRfcommChannel.PRIMARY.required)
        assertEquals(
            listOf("primary", "extra", "record"),
            HeyCyanRfcommChannel.CANDIDATES.map { it.name }
        )
        assertTrue(HeyCyanRfcommChannel.CANDIDATES.drop(1).all { !it.required })
    }
}

private val UUID_PLACEHOLDER: UUID? = null
