package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HeyCyanRecordingCaptureTest {
    private val notify = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
    private val router = HeyCyanEventRouter(HeyCyanResponseDecoder())
    private val reducer = HeyCyanDeviceStateReducer()
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun apply(state: HeyCyanDeviceState, hex: String) = reducer.reduce(state, router.decode(notify, bytes(hex)), 42)

    // Real selected A2, 2026-11-13: bounded five-second recordings, then fresh
    // inventory increased by one video/audio respectively. Vendor guide pp9-10
    // describes the start/stop acknowledgements separately from busy responses.
    @Test fun capturedVideoStartExposesStopControl() {
        val state = apply(HeyCyanDeviceState(), "bc410500fc4c020102ffff")
        assertEquals(VideoRecordingState.RECORDING, state.videoRecordingState)
        assertEquals(HeyCyanMode.VIDEO, state.mode)
    }
    @Test fun capturedVideoStopClearsOnlyVideoRecording() {
        val state = apply(HeyCyanDeviceState(mode = HeyCyanMode.VIDEO,
            videoRecordingState = VideoRecordingState.RECORDING), "bc4105006c0d020103ff02")
        assertEquals(VideoRecordingState.IDLE, state.videoRecordingState)
        assertNull(state.mode)
    }
    @Test fun capturedAudioStartExposesStopControl() {
        val state = apply(HeyCyanDeviceState(), "bc410500dc4e020108ffff")
        assertEquals(AudioRecordingState.RECORDING, state.audioRecordingState)
        assertEquals(HeyCyanMode.AUDIO, state.mode)
    }
    @Test fun capturedAudioStopClearsOnlyAudioRecording() {
        val state = apply(HeyCyanDeviceState(mode = HeyCyanMode.AUDIO,
            audioRecordingState = AudioRecordingState.RECORDING), "bc410500dc0902010cff08")
        assertEquals(AudioRecordingState.IDLE, state.audioRecordingState)
        assertNull(state.mode)
    }
    @Test fun invalidFramesDoNotChangeRecordingState() {
        val captured = bytes("bc410500fc4c020102ffff")
        val damaged = captured.copyOf().also { it[4] = (it[4].toInt() xor 1).toByte() }
        val before = HeyCyanDeviceState()
        for (frame in listOf(damaged, captured.copyOf(9))) {
            assertEquals(before.videoRecordingState, reducer.reduce(before, router.decode(notify, frame), 42).videoRecordingState)
        }
        assertArrayEquals(bytes("bc410500fc4c020102ffff"), captured)
    }
    @Test fun busyAndUnobservedRepliesRemainRaw() {
        // Negative robustness cases, not new protocol evidence.
        for (payload in listOf(byteArrayOf(2, 1, 2, 0, 8), byteArrayOf(2, 1, 2, -1, 2))) {
            val frame = HeyCyanWireFrameCodec.encode(0x41, payload)
            assertTrue(router.decode(notify, frame) is HeyCyanEvent.DecodedProprietaryFrame)
        }
    }
}
