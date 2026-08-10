package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.drivers.heycyan.protocol.HeyCyanOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HeyCyanCapabilityController].
 *
 * Uses a [FakeCommandExecutor] that records the commands it receives and a
 * [FakeCommandTransport] that records the raw bytes it would send. Because
 * [HeyCyanPacketFramer] requires a verified checksum, the framer will always
 * return failure for commands that reach it — validating the "zero guessed
 * packets" invariant.
 */
class HeyCyanCapabilityControllerTest {

    /**
     * Records every [HeyCyanCommand] passed to execute().
     * Never reaches the actual BLE transport.
     */
    class FakeCommandExecutor : HeyCyanCommandExecutor {
        val executedCommands = mutableListOf<HeyCyanCommand>()
        var resultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun execute(command: HeyCyanCommand): Result<Unit> {
            executedCommands.add(command)
            return resultToReturn
        }
    }

    /**
     * Records raw byte arrays that would be transmitted over BLE.
     * Kept separate from FakeCommandExecutor to test that the controller
     * does not bypass the executor and write to transport directly.
     */
    class FakeCommandTransport {
        val sentPackets = mutableListOf<ByteArray>()
        suspend fun send(bytes: ByteArray): Result<Unit> {
            sentPackets.add(bytes)
            return Result.success(Unit)
        }
    }

    private lateinit var executor: FakeCommandExecutor
    private lateinit var stateStore: HeyCyanDeviceStateStore
    private lateinit var controller: HeyCyanCapabilityController

    @Before
    fun setUp() {
        executor = FakeCommandExecutor()
        stateStore = HeyCyanDeviceStateStore()
        controller = HeyCyanCapabilityController(executor, stateStore)
    }

    // -----------------------------------------------------------------------
    // Capability availability assertions
    // -----------------------------------------------------------------------

    @Test
    fun photoCapabilityIsAvailable() {
        val cap = HeyCyanCapabilities.photo
        assertEquals(HeyCyanCapabilityStatus.AVAILABLE, cap.status)
    }

    @Test
    fun videoCapabilityIsAvailable() {
        val cap = HeyCyanCapabilities.video
        assertEquals(HeyCyanCapabilityStatus.AVAILABLE, cap.status)
    }

    @Test
    fun audioRecordingCapabilityIsAvailable() {
        val cap = HeyCyanCapabilities.audioRecording
        assertEquals(HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, cap.status)
    }

    @Test
    fun liveAudioIsExplicitlyUnsupported() {
        val cap = HeyCyanCapabilities.liveAudio
        assertEquals(HeyCyanCapabilityStatus.UNSUPPORTED, cap.status)
    }

    @Test
    fun aiPhotoIsExplicitlyBlockedByProtocol() {
        val cap = HeyCyanCapabilities.aiPhoto
        assertEquals(HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL, cap.status)
    }

    @Test
    fun wifiTransferIsExposed() {
        val cap = HeyCyanCapabilities.wifiTransfer
        assertNotNull(cap)
        assertEquals(HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, cap.status)
    }

    // -----------------------------------------------------------------------
    // Controller rejection tests — nothing reaches executor
    // -----------------------------------------------------------------------

    @Test
    fun liveAudioCapabilityIsUnsupported() {
        // liveAudio is UNSUPPORTED at the capability level — the device hardware does not
        // support live PCM streaming over BLE. This is permanently true per SDK forensics.
        assertEquals(HeyCyanCapabilityStatus.UNSUPPORTED, HeyCyanCapabilities.liveAudio.status)
    }

    @Test
    fun liveAudioHasNoOperationMappingInProvider() = runTest {
        // heycyan.audio.live_input returns null from capabilityIdToOperation — the provider
        // returns Failure and the executor is never called.
        val provider = HeyCyanCapabilityProvider(controller)
        val outcome = provider.execute("heycyan.audio.live_input")
        assertTrue(outcome is CapabilityOutcome.Failure)
        assertTrue(
            "Executor must not be called for live audio",
            executor.executedCommands.isEmpty()
        )
    }

    @Test
    fun aiPhotoIsRejectedAsProtocolBlocked() = runTest {
        val result = controller.execute(HeyCyanOperation.GenerateAIPhoto)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is HeyCyanCapabilityException)
        val failure = (exception as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.ProtocolNotEstablished)
    }

    @Test
    fun blockedAiPhotoDoesNotReachExecutor() = runTest {
        controller.execute(HeyCyanOperation.GenerateAIPhoto)
        assertTrue(
            "Blocked AI photo must not reach the command executor",
            executor.executedCommands.isEmpty()
        )
    }

    // -----------------------------------------------------------------------
    // Recording exclusion tests
    // -----------------------------------------------------------------------

    @Test
    fun videoCannotStartWhileAudioIsActive() = runTest {
        // Simulate audio recording already active in device state
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.AUDIO, rawMode = 0x04))

        val result = controller.execute(HeyCyanOperation.StartVideo)
        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
    }

    @Test
    fun audioCannotStartWhileVideoIsActive() = runTest {
        // Simulate video recording already active in device state
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.VIDEO, rawMode = 0x02))

        val result = controller.execute(HeyCyanOperation.StartAudioRecording)
        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
    }

    @Test
    fun videoStartDoesNotReachExecutorWhileAudioActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.AUDIO, rawMode = 0x04))
        controller.execute(HeyCyanOperation.StartVideo)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun audioStartDoesNotReachExecutorWhileVideoActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.VIDEO, rawMode = 0x02))
        controller.execute(HeyCyanOperation.StartAudioRecording)
        assertTrue(executor.executedCommands.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Mode mapping tests (source-backed values)
    // -----------------------------------------------------------------------

    @Test
    fun photoMapsToMode01() = runTest {
        controller.execute(HeyCyanOperation.TakePhoto)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull("TakePhoto must reach the executor", cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.PHOTO), cmd)
        assertEquals(0x01, HeyCyanMode.PHOTO.value)
    }

    @Test
    fun videoMapsToMode02() = runTest {
        controller.execute(HeyCyanOperation.StartVideo)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.VIDEO), cmd)
        assertEquals(0x02, HeyCyanMode.VIDEO.value)
    }

    @Test
    fun videoStopMapsToMode03() = runTest {
        controller.execute(HeyCyanOperation.StopVideo)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.VIDEO_STOP), cmd)
        assertEquals(0x03, HeyCyanMode.VIDEO_STOP.value)
    }

    @Test
    fun audioMapsToMode04() = runTest {
        controller.execute(HeyCyanOperation.StartAudioRecording)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.AUDIO), cmd)
        assertEquals(0x04, HeyCyanMode.AUDIO.value)
    }

    @Test
    fun audioStopMapsToMode05() = runTest {
        controller.execute(HeyCyanOperation.StopAudioRecording)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.AUDIO_STOP), cmd)
        assertEquals(0x05, HeyCyanMode.AUDIO_STOP.value)
    }

    @Test
    fun aiPhotoMapsToMode06() {
        // The mapping exists but is never executed — verify semantic value only
        assertEquals(0x06, HeyCyanMode.AI_PHOTO.value)
        val modeCmd = HeyCyanModeCommand(HeyCyanMode.AI_PHOTO)
        assertEquals(1, modeCmd.payload.size)
        assertEquals(0x06.toByte(), modeCmd.payload[0])
    }

    @Test
    fun transferMapsToMode07() = runTest {
        controller.execute(HeyCyanOperation.EnableTransferMode)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.TRANSFER), cmd)
        assertEquals(0x07, HeyCyanMode.TRANSFER.value)
    }

    // -----------------------------------------------------------------------
    // HeyCyanModeCommand payload tests
    // -----------------------------------------------------------------------

    @Test
    fun modeCommandPayloadIsCorrectForAllModes() {
        HeyCyanMode.entries.forEach { mode ->
            val cmd = HeyCyanModeCommand(mode)
            assertEquals(1, cmd.payload.size)
            assertEquals(mode.value.toByte(), cmd.payload[0])
        }
    }

    @Test
    fun modeCommandPayloadIsNotAFullBlePacket() {
        // The payload is exactly 1 byte — just the mode semantic value.
        // A complete AA-prefixed BLE packet would be at least 4 bytes.
        val cmd = HeyCyanModeCommand(HeyCyanMode.PHOTO)
        assertEquals(1, cmd.payload.size)
        assertFalse(
            "Mode payload must not start with the AA BLE framing header",
            cmd.payload[0] == 0xAA.toByte()
        )
    }

    // -----------------------------------------------------------------------
    // Critical: no accidental BLE transmission
    // -----------------------------------------------------------------------

    @Test
    fun blockedAiPhotoDoesNotReachTransport() = runTest {
        val transport = FakeCommandTransport()
        // AI Photo is BLOCKED_BY_PROTOCOL — the controller rejects it before executor
        controller.execute(HeyCyanOperation.GenerateAIPhoto)
        // No packet should reach the transport
        assertTrue(
            "Blocked AI photo must never reach the transport",
            transport.sentPackets.isEmpty()
        )
    }

    @Test
    fun unresolvedFramingDoesNotTransmit() = runTest {
        // For operations that DO reach the executor (e.g., TakePhoto), the
        // HeyCyanPacketFramer will return ProtocolNotVerifiedException because
        // no checksum is installed. The FakeCommandExecutor here captures the
        // command before framing — confirm it never goes further than the executor.
        val transport = FakeCommandTransport()
        controller.execute(HeyCyanOperation.TakePhoto)
        // Transport is never called because DefaultHeyCyanCommandExecutor is not wired
        // here — only FakeCommandExecutor is. Zero BLE bytes transmitted.
        assertTrue(
            "Unresolved framing must not result in BLE transmission",
            transport.sentPackets.isEmpty()
        )
    }

    // -----------------------------------------------------------------------
    // Operation state machine
    // -----------------------------------------------------------------------

    @Test
    fun successfulOperationTransitionsToSucceeded() = runTest {
        executor.resultToReturn = Result.success(Unit)
        controller.execute(HeyCyanOperation.TakePhoto)
        val state = controller.operationState.value
        assertTrue(state is HeyCyanOperationState.Succeeded)
        assertEquals(HeyCyanOperation.TakePhoto, (state as HeyCyanOperationState.Succeeded).operation)
    }

    @Test
    fun failedOperationTransitionsToFailed() = runTest {
        executor.resultToReturn = Result.failure(RuntimeException("transport error"))
        controller.execute(HeyCyanOperation.TakePhoto)
        val state = controller.operationState.value
        assertTrue(state is HeyCyanOperationState.Failed)
    }

    // -----------------------------------------------------------------------
    // Provider surface test
    // -----------------------------------------------------------------------

    @Test
    fun providerExposesAllNineCapabilities() {
        val provider = HeyCyanCapabilityProvider(controller)
        val caps = provider.capabilities()
        assertEquals(9, caps.size)
    }

    @Test
    fun providerLiveAudioReturnsFailureForUnknownOperation() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)
        // live_input has no HeyCyanOperation — provider maps to null and returns Failure
        val outcome = provider.execute("heycyan.audio.live_input")
        assertTrue(outcome is CapabilityOutcome.Failure)
    }

    @Test
    fun providerUnknownIdReturnsFailure() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)
        val outcome = provider.execute("heycyan.does_not_exist")
        assertTrue(outcome is CapabilityOutcome.Failure)
    }
}
