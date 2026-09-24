package com.halo.drivers.heycyan.thumbnail

import com.halo.drivers.heycyan.arbiter.HeyCyanResource
import com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter
import com.halo.drivers.heycyan.codec.HeyCyanWireFrameCodec
import com.halo.drivers.heycyan.codec.WireFrameDecodeResult
import com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanThumbnailPipelineTest {

    @Test
    fun `buildChunkRequest builds valid wire frame with Little-Endian index`() {
        val request = HeyCyanThumbnailTransport.buildChunkRequest(index = 258) // 0x0102 -> low=0x02, high=0x01
        val decodeResult = HeyCyanWireFrameCodec.decode(request)

        assertTrue(decodeResult is WireFrameDecodeResult.Success)
        val success = decodeResult as WireFrameDecodeResult.Success
        assertEquals(HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS, success.action)
        assertEquals(3, success.payloadLength)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x01), success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test
    fun `decodeChunk decodes valid response wire frame`() {
        val chunkPayload = byteArrayOf(
            0x01,
            0x02, 0x00, // total = 2
            0x00, 0x00, // currIndex = 0
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
        )
        val frame = HeyCyanWireFrameCodec.encode(
            action = HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS,
            payload = chunkPayload,
        )

        val chunk = HeyCyanThumbnailTransport.decodeChunk(frame)
        assertNotNull(chunk)
        assertEquals(2, chunk!!.total)
        assertEquals(0, chunk.currIndex)
        assertFalse(chunk.isComplete)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), chunk.data)
    }

    @Test
    fun `assembler accumulates chunks and enforces size limits`() {
        val assembler = HeyCyanThumbnailAssembler(minBytes = 4L, maxBytes = 20L)

        val chunk0 = HeyCyanThumbnailChunk(
            total = 2,
            currIndex = 0,
            data = byteArrayOf(1, 2, 3),
            isComplete = false,
        )
        val status0 = assembler.addChunk(chunk0)
        assertTrue(status0 is HeyCyanThumbnailAssembler.AssemblyStatus.InProgress)
        assertEquals(1, (status0 as HeyCyanThumbnailAssembler.AssemblyStatus.InProgress).nextNeededIndex)

        val chunk1 = HeyCyanThumbnailChunk(
            total = 2,
            currIndex = 1,
            data = byteArrayOf(4, 5, 6),
            isComplete = true,
        )
        val status1 = assembler.addChunk(chunk1)
        assertTrue(status1 is HeyCyanThumbnailAssembler.AssemblyStatus.Completed)
        val completed = status1 as HeyCyanThumbnailAssembler.AssemblyStatus.Completed
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), completed.bytes)
    }

    @Test
    fun `assembler rejects oversized transfers`() {
        val assembler = HeyCyanThumbnailAssembler(minBytes = 2L, maxBytes = 5L)
        val chunk = HeyCyanThumbnailChunk(
            total = 2,
            currIndex = 0,
            data = ByteArray(6),
            isComplete = false,
        )
        val status = assembler.addChunk(chunk)
        assertTrue(status is HeyCyanThumbnailAssembler.AssemblyStatus.Failed)
    }

    @Test
    fun `full capture coordinator flow executes successfully with mock transport`() = runTest {
        val eventFlow = MutableSharedFlow<HeyCyanEvent>(replay = 1, extraBufferCapacity = 16)
        val rawFramesFlow = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 16)
        val arbiter = HeyCyanResourceArbiter()

        // Build valid synthetic JPEG payload (>= 1024 bytes with SOI FF D8 and EOI FF D9)
        val syntheticJpeg = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xD8)
            write(ByteArray(1022) { 0x42 })
            write(0xFF)
            write(0xD9)
        }.toByteArray()

        val half = syntheticJpeg.size / 2
        val slice0 = syntheticJpeg.copyOfRange(0, half)
        val slice1 = syntheticJpeg.copyOfRange(half, syntheticJpeg.size)

        val chunkFrame0 = HeyCyanWireFrameCodec.encode(
            action = HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS,
            payload = byteArrayOf(0x01, 0x02, 0x00, 0x00, 0x00) + slice0,
        )
        val chunkFrame1 = HeyCyanWireFrameCodec.encode(
            action = HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS,
            payload = byteArrayOf(0x01, 0x02, 0x00, 0x01, 0x00) + slice1,
        )

        val sentCommands = mutableListOf<ByteArray>()
        val commandTransport = object : HeyCyanCommandTransport {
            override suspend fun send(bytes: ByteArray): Result<Unit> {
                sentCommands.add(bytes)
                val decoded = HeyCyanWireFrameCodec.decode(bytes)
                if (decoded is WireFrameDecodeResult.Success &&
                    decoded.action == HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS &&
                    decoded.payload.size >= 3
                ) {
                    val index = (decoded.payload[1].toInt() and 0xFF) or ((decoded.payload[2].toInt() and 0xFF) shl 8)
                    if (index == 0) {
                        rawFramesFlow.emit(chunkFrame0)
                    } else if (index == 1) {
                        rawFramesFlow.emit(chunkFrame1)
                    }
                }
                return Result.success(Unit)
            }
        }

        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val coordinator = HeyCyanThumbnailCaptureCoordinator(
            commandTransport = commandTransport,
            incomingEvents = eventFlow,
            incomingRawFrames = rawFramesFlow,
            resourceArbiter = arbiter,
            photoReadyTimeoutMs = 1_000L,
            settleDelayMs = 10L,
            transferTimeoutMs = 2_000L,
            chunkTimeoutMs = 500L,
            dispatcher = testDispatcher,
        )

        val captureJob = launch(testDispatcher) {
            val result = coordinator.captureThumbnail(quality = 3)
            assertTrue("Capture failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val thumbnail = result.getOrThrow()
            assertArrayEquals(syntheticJpeg, thumbnail.bytes)
        }

        // Simulate glasses photo-ready event
        eventFlow.emit(HeyCyanEvent.AiImageRecognition)

        captureJob.join()

        // Verify resource lease was released
        assertFalse(arbiter.hasActiveLease(HeyCyanResource.CAMERA))
        assertFalse(arbiter.hasActiveLease(HeyCyanResource.BLE_BULK_TRANSFER))
    }

    @Test
    fun `capture coordinator fails immediately if conflicting lease active`() = runTest {
        val commandTransport = object : HeyCyanCommandTransport {
            override suspend fun send(bytes: ByteArray): Result<Unit> = Result.success(Unit)
        }
        val eventFlow = MutableSharedFlow<HeyCyanEvent>()
        val rawFramesFlow = MutableSharedFlow<ByteArray>()
        val arbiter = HeyCyanResourceArbiter()

        // Hold video recorder lease (conflicts with CAMERA)
        val videoLease = arbiter.tryAcquire("video", setOf(HeyCyanResource.VIDEO_RECORDER))
        assertNotNull(videoLease)

        val coordinator = HeyCyanThumbnailCaptureCoordinator(
            commandTransport = commandTransport,
            incomingEvents = eventFlow,
            incomingRawFrames = rawFramesFlow,
            resourceArbiter = arbiter,
        )

        val result = coordinator.captureThumbnail()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        videoLease!!.release()
    }
}
