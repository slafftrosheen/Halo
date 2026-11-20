package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HeyCyanPendingInventoryCaptureTest {
    private val notify = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
    private val router = HeyCyanEventRouter(HeyCyanResponseDecoder())
    // Real A2 GetMediaInventory reply, 2026-11-13. Vendor SDK guide p13:
    // dataType 4 reports unsynced image/video/record counts. No identifier/media bytes.
    private fun emptyCapture() = "bc410b001d490204000000000000010000"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun realEmptyInventoryReachesTheLiveEventOwner() {
        val frame = emptyCapture()
        val original = frame.copyOf()
        val result = router.decode(notify, frame)
        assertTrue("Captured count reply was lost as $result", result is HeyCyanEvent.MediaCountsUpdated)
        result as HeyCyanEvent.MediaCountsUpdated
        assertEquals(0, result.photoCount)
        assertEquals(0, result.videoCount)
        assertEquals(0, result.audioCount)
        assertEquals(4, result.mediaType)
        assertArrayEquals(original, frame)
    }

    @Test fun damagedOrTruncatedRepliesCannotAuthorizeAnEmptyList() {
        val damaged = emptyCapture().also { it[4] = (it[4].toInt() xor 1).toByte() }
        assertFalse(router.decode(notify, damaged) is HeyCyanEvent.MediaCountsUpdated)
        assertFalse(router.decode(notify, emptyCapture().copyOf(13)) is HeyCyanEvent.MediaCountsUpdated)
    }

    @Test fun realCountsAfterTheVideoAndAudioClipsRemainNonempty() {
        for ((hex, audio) in listOf("bc410b000d890204000001000000010000" to 0,
            "bc410b0030490204000001000100010000" to 1)) {
            val frame = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val counts = router.decode(notify, frame) as HeyCyanEvent.MediaCountsUpdated
            assertEquals(0, counts.photoCount)
            assertEquals(1, counts.videoCount)
            assertEquals(audio, counts.audioCount)
            assertEquals(1 + audio, counts.total)
        }
    }

    @Test fun unrelatedCharacteristicCannotAuthorizeAnEmptyList() {
        assertFalse(router.decode(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"), emptyCapture())
            is HeyCyanEvent.MediaCountsUpdated)
    }
}
