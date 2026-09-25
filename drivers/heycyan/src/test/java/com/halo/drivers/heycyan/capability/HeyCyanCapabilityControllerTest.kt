package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.drivers.heycyan.protocol.toCommand
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for [HeyCyanCapabilityController]. */
class HeyCyanCapabilityControllerTest {

    class FakeCommandExecutor : HeyCyanCommandExecutor {
        val executedCommands = mutableListOf<HeyCyanCommand>()
        var resultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun execute(command: HeyCyanCommand): Result<Unit> {
            executedCommands.add(command)
            return resultToReturn
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
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE,
            HeyCyanCapabilities.photo.status,
        )
    }

    @Test
    fun videoCapabilityIsAvailable() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE,
            HeyCyanCapabilities.video.status,
        )
    }

    @Test
    fun audioRecordingCapabilityIsAvailableWithLimitation() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
            HeyCyanCapabilities.audioRecording.status,
        )
    }

    @Test
    fun liveAudioIsAvailableWithLimitation() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
            HeyCyanCapabilities.liveAudio.status,
        )
    }

    @Test
    fun aiPhotoIsAvailableWithLimitation() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
            HeyCyanCapabilities.aiPhoto.status,
        )
    }

    @Test
    fun wifiTransferIsExposedWithLimitation() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
            HeyCyanCapabilities.wifiTransfer.status,
        )
    }

    @Test
    fun `speaker output is exposed with limitation`() {
        assertEquals(
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
            HeyCyanCapabilities.speakerOutput.status,
        )
    }

    @Test
    fun `canonical capability ids contain exactly the exposed capability surface`() {
        assertEquals(
            HeyCyanCapabilityIds.all,
            HeyCyanCapabilities.all.map { it.id }.toSet(),
        )
    }

    @Test
    fun `every exposed capability has a canonical id`() {
        HeyCyanCapabilities.all.forEach { capability ->
            assertTrue(capability.id in HeyCyanCapabilityIds.all)
        }
    }

    @Test
    fun `video aggregate capability is not directly executable`() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)

        val result = provider.execute(HeyCyanCapabilityIds.CAMERA_VIDEO)

        assertTrue(result is CapabilityOutcome.Failure)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `audio aggregate capability is not directly executable`() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)

        val result = provider.execute(HeyCyanCapabilityIds.AUDIO_RECORDING)

        assertTrue(result is CapabilityOutcome.Failure)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `live audio never reaches command executor`() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)

        val result = provider.execute(HeyCyanCapabilityIds.AUDIO_LIVE_INPUT)

        assertTrue(result is CapabilityOutcome.Failure)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `ai photo never reaches command executor`() = runTest {
        val provider = HeyCyanCapabilityProvider(controller)

        val result = provider.execute(HeyCyanCapabilityIds.AI_PHOTO)

        assertTrue(result is CapabilityOutcome.Failure)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `speaker output is rejected until transport is ready`() = runTest {
        val result = controller.execute(HeyCyanOperation.RouteAudioToGlasses)

        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `speaker output reaches executor when transport is ready`() = runTest {
        stateStore.updateConnection(HeyCyanConnectionState.READY)

        val result = controller.execute(HeyCyanOperation.RouteAudioToGlasses)

        assertTrue(result.isSuccess)
        assertEquals(HeyCyanCommand.RouteAudioToGlasses, executor.executedCommands.single())
    }

    @Test
    fun `synchronize media is never translated to a BLE command`() = runTest {
        val result = controller.execute(HeyCyanOperation.SynchronizeMedia)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is HeyCyanCapabilityException)
        val failure = (exception as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.Unsupported)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `synchronize media command mapping fails fast`() {
        val exception = runCatching {
            HeyCyanOperation.SynchronizeMedia.toCommand()
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `prepare voice routing reaches the verified command path`() = runTest {
        val result = controller.execute(HeyCyanOperation.PrepareVoiceRouting)

        assertTrue(result.isSuccess)
        assertEquals(HeyCyanCommand.PrepareVoiceRouting, executor.executedCommands.single())
    }

    @Test
    fun aiPhotoIsRejectedAsProtocolBlocked() = runTest {
        val result = controller.execute(HeyCyanOperation.GenerateAIPhoto)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HeyCyanCapabilityException)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun videoCannotStartWhileAudioIsActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.AUDIO.value))

        val result = controller.execute(HeyCyanOperation.StartVideo)

        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun audioCannotStartWhileVideoIsActive() = runTest {
        stateStore.apply(HeyCyanEvent.ModeChanged(mode = HeyCyanMode.VIDEO.value))

        val result = controller.execute(HeyCyanOperation.StartRecording)

        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as HeyCyanCapabilityException).failure
        assertTrue(failure is HeyCyanOperationFailure.DeviceRejected)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun photoMapsToCommand() = runTest {
        controller.execute(HeyCyanOperation.TakePhoto)
        assertEquals(HeyCyanCommand.TakePhoto, executor.executedCommands.first())
    }

    @Test
    fun videoOperationsMapToConcreteCommands() {
        assertEquals(
            HeyCyanCommand.StartVideo,
            HeyCyanOperation.StartVideo.toCommand(),
        )
        assertEquals(
            HeyCyanCommand.StopVideo,
            HeyCyanOperation.StopVideo.toCommand(),
        )
    }

    @Test
    fun audioOperationsMapToConcreteCommands() {
        assertEquals(
            HeyCyanCommand.StartRecording,
            HeyCyanOperation.StartRecording.toCommand(),
        )
        assertEquals(
            HeyCyanCommand.StopRecording,
            HeyCyanOperation.StopRecording.toCommand(),
        )
    }

    @Test
    fun transferMapsToExplicitWifiTransferCommand() = runTest {
        controller.execute(HeyCyanOperation.EnterWifiTransferMode)
        assertEquals(
            HeyCyanCommand.EnterWifiTransferMode,
            executor.executedCommands.first(),
        )
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
        controller.execute(HeyCyanOperation.TakePhoto)

        val state = controller.operationState.value
        assertTrue(state is HeyCyanOperationState.Succeeded)
        assertEquals(
            HeyCyanOperation.TakePhoto,
            (state as HeyCyanOperationState.Succeeded).operation,
        )
    }

    @Test
    fun failedOperationTransitionsToFailed() = runTest {
        executor.resultToReturn = Result.failure(RuntimeException("transport error"))

        controller.execute(HeyCyanOperation.TakePhoto)

        val state = controller.operationState.value
        assertTrue(state is HeyCyanOperationState.Failed)
    }

    @Test
    fun providerExposesExactlyCanonicalCapabilitySurface() {
        val provider = HeyCyanCapabilityProvider(controller)
        val caps = provider.capabilities()

        assertEquals(HeyCyanCapabilities.all.size, caps.size)
        assertEquals(
            HeyCyanCapabilityIds.all,
            caps.map { it.id }.toSet(),
        )
    }
}
