package com.halo.drivers.heycyan.transport.rfcomm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoMindPacketDecoderTest {

    private lateinit var encoder: MemoMindPacketEncoder
    private lateinit var decoder: MemoMindPacketDecoder

    @Before
    fun setUp() {
        encoder = MemoMindPacketEncoder()
        decoder = MemoMindPacketDecoder()
    }

    @Test
    fun decodesSinglePacket() {
        val packet = MemoMindPacket(command = 0x13, payload = byteArrayOf(0x50))
        val encoded = encoder.encode(packet)

        val decodedList = decoder.feed(encoded)
        assertEquals(1, decodedList.size)
        assertEquals(packet, decodedList.first())
    }

    @Test
    fun decodesPacketSplitAcrossReads() {
        val packet = MemoMindPacket(command = 0x01, payload = byteArrayOf(0x02, 0x03))
        val encoded = encoder.encode(packet)

        val half1 = encoded.copyOfRange(0, 3)
        val half2 = encoded.copyOfRange(3, encoded.size)

        val res1 = decoder.feed(half1)
        assertTrue(res1.isEmpty())

        val res2 = decoder.feed(half2)
        assertEquals(1, res2.size)
        assertEquals(packet, res2.first())
    }

    @Test
    fun decodesMultiplePacketsInSingleRead() {
        val packet1 = MemoMindPacket(command = 0x10, payload = byteArrayOf())
        val packet2 = MemoMindPacket(command = 0x14, payload = byteArrayOf())

        val bytes1 = encoder.encode(packet1)
        val bytes2 = encoder.encode(packet2)
        val combined = bytes1 + bytes2

        val decoded = decoder.feed(combined)
        assertEquals(2, decoded.size)
        assertEquals(packet1, decoded[0])
        assertEquals(packet2, decoded[1])
    }

    @Test
    fun recoversFromGarbagePrecedingHeader() {
        val packet = MemoMindPacket(command = 0x13, payload = byteArrayOf())
        val encoded = encoder.encode(packet)
        val garbage = byteArrayOf(0x12, 0x34, 0x56)
        val noisyStream = garbage + encoded

        val decoded = decoder.feed(noisyStream)
        assertEquals(1, decoded.size)
        assertEquals(packet, decoded.first())
    }
}
