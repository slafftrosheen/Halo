package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PacketCorrelationTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `reports inserted action packet without interpreting its bytes`() {
        val baseline = capture(
            packet(sequence = 0, payload = byteArrayOf(0x10))
        )
        val action = capture(
            packet(sequence = 0, payload = byteArrayOf(0x10)),
            packet(sequence = 1, payload = byteArrayOf(0x20))
        )

        val differences = OrderedPacketCorrelation().compare(baseline, action)

        assertEquals(1, differences.size)
        assertEquals(1L, differences.single().sequence)
        assertArrayEquals(byteArrayOf(0x20), differences.single().actionPayload)
        assertNull(differences.single().baselinePayload)
    }

    @Test
    fun `aligns independent characteristic streams separately`() {
        val otherCharacteristic = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val baseline = capture(
            packet(sequence = 0, characteristic = characteristic, payload = byteArrayOf(0x01)),
            packet(sequence = 1, characteristic = otherCharacteristic, payload = byteArrayOf(0x02))
        )
        val action = capture(
            packet(sequence = 0, characteristic = characteristic, payload = byteArrayOf(0x03)),
            packet(sequence = 1, characteristic = otherCharacteristic, payload = byteArrayOf(0x02))
        )

        val differences = OrderedPacketCorrelation().compare(baseline, action)

        assertEquals(1, differences.size)
        assertEquals(characteristic, differences.single().characteristicUuid)
        assertArrayEquals(byteArrayOf(0x01), differences.single().baselinePayload)
        assertArrayEquals(byteArrayOf(0x03), differences.single().actionPayload)
    }

    @Test
    fun `returns no differences for identical raw packet streams`() {
        val baseline = capture(packet(sequence = 0, payload = byteArrayOf(0x01, 0x02)))
        val action = capture(packet(sequence = 7, payload = byteArrayOf(0x01, 0x02)))

        assertEquals(emptyList<PacketDifference>(), OrderedPacketCorrelation().compare(baseline, action))
    }

    private fun capture(vararg packets: BlePacket): BleCapture = BleCapture(
        captureId = "test",
        startedAt = 0,
        packets = packets.toList()
    )

    private fun packet(
        sequence: Long,
        characteristic: UUID = this.characteristic,
        payload: ByteArray
    ): BlePacket = BlePacket(
        sequence = sequence,
        timestamp = sequence,
        direction = PacketDirection.RX,
        characteristic = characteristic,
        payload = payload
    )
}
