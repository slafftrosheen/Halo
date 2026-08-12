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
 */
class HeyCyanCapabilityControllerTest {

    class FakeCommandExecutor : HeyCyanCommandExecutor {
        val executedCommands = mutableListOf<HeyCyanCommand>()
        var resultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun execute(command: HeyCyanCommand): Result<Unit> {
            executedCommands.add(command)
            return resultToReturn
        }
    }

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
        com.halo.drivers.heycyan.debug.HeyCyanLogger.logger = { _, _ -> }
        executor = FakeCommandExecutor()
        stateStore = HeyCyanDeviceStateStore()
        controller = HeyCyanCapabilityController(executor, stateStore)
    }

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

    @Test
    fun liveAudioHasNoOperationMappingInProvider() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)
        val outcome = provider.execute("heycyan.audio.live_input")
        assertTrue(outcome is CapabilityOutcome.Failure)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun aiPhotoIsRejectedAsProtocolBlocked() = runTest {
        val result = controller.execute(HeyCyanOperation.GenerateAIPhoto)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is HeyCyanCapabilityException)
    }

    @Test
    fun videoCannotToggleWhileAudioIsActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.AUDIO.value))
        val result = controller.execute(HeyCyanOperation.ToggleVideo)
        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
    }

    @Test
    fun audioCannotToggleWhileVideoIsActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.VIDEO.value))
        val result = controller.execute(HeyCyanOperation.PrepareVoiceRouting)
        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
    }

    @Test
    fun photoMapsToCommand() = runTest {
        controller.execute(HeyCyanOperation.TakePhoto)
        assertEquals(HeyCyanCommand.TakePhoto, executor.executedCommands.first())
    }

    @Test
    fun videoToggleMapsToCommand() = runTest {
        controller.execute(HeyCyanOperation.ToggleVideo)
        assertEquals(HeyCyanCommand.ToggleVideo, executor.executedCommands.first())
    }

    @Test
    fun audioToggleMapsToCommand() = runTest {
        controller.execute(HeyCyanOperation.PrepareVoiceRouting)
        assertEquals(HeyCyanCommand.PrepareVoiceRouting, executor.executedCommands.first())
    }

    @Test
    fun transferMapsToMode07() = runTest {
        controller.execute(HeyCyanOperation.EnterWifiTransferMode)
        val cmd = executor.executedCommands.firstOrNull()
        assertNotNull(cmd)
        assertEquals(HeyCyanCommand.EnterWifiTransferMode, cmd)
    }

    @Test
    fun modeCommandPayloadIsCorrectForAllModes() {
        HeyCyanMode.entries.forEach { mode ->
            val cmd = HeyCyanModeCommand(mode)
            assertEquals(1, cmd.payload.size)
            assertEquals(mode.value.toByte(), cmd.payload[0])
        }
    }

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

    @Test
    fun providerExposesAllCapabilities() {
        val provider = HeyCyanCapabilityProvider(controller)
        val caps = provider.capabilities()
        // battery, deviceInfo, mediaInventory, photo, video, audioRecording, liveAudio, aiPhoto, wifiTransfer
        assertEquals(9, caps.size)
    }
}
