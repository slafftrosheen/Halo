package com.halo.drivers.heycyan.thumbnail

import android.graphics.BitmapFactory
import com.halo.drivers.heycyan.arbiter.HeyCyanResource
import com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter
import com.halo.drivers.heycyan.codec.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class HeyCyanCapturedThumbnail(
    val bytes: ByteArray,
    val quality: Int,
    val commandAtMs: Long?,
    val firstChunkAtMs: Long? = null,
    val photoReadyAtMs: Long,
    val completedAtMs: Long,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeyCyanCapturedThumbnail) return false
        if (quality != other.quality) return false
        if (commandAtMs != other.commandAtMs) return false
        if (firstChunkAtMs != other.firstChunkAtMs) return false
        if (photoReadyAtMs != other.photoReadyAtMs) return false
        if (completedAtMs != other.completedAtMs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + quality
        result = 31 * result + commandAtMs.hashCode()
        result = 31 * result + firstChunkAtMs.hashCode()
        result = 31 * result + photoReadyAtMs.hashCode()
        result = 31 * result + completedAtMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

interface HeyCyanThumbnailCapture {
    suspend fun capture(quality: Int = 3): Result<HeyCyanCapturedThumbnail>
}

/**
 * End-to-end coordinator for the fast BLE AI-thumbnail capture pipeline.
 *
 * Sequence:
 * 1. Acquires CAMERA + BLE_BULK_TRANSFER resources from [HeyCyanResourceArbiter].
 * 2. Arms the photo-ready notification listener.
 * 3. Dispatches verified 5-byte Chat-AI thumbnail command: 02 01 06 q q.
 * 4. Awaits hardware exposure & compression confirmation (Photo-Ready 0x02 notify).
 * 5. Applies hardware settling delay (default 200ms).
 * 6. Iteratively pulls and reassembles 0xFD slices via [HeyCyanThumbnailTransport].
 * 7. Validates JPEG structure and dimensions before completing.
 * 8. Releases hardware lease deterministically.
 */
class HeyCyanThumbnailCaptureCoordinator(
    private val commandTransport: HeyCyanCommandTransport,
    private val incomingEvents: Flow<HeyCyanEvent>,
    private val incomingRawFrames: Flow<ByteArray>,
    private val resourceArbiter: HeyCyanResourceArbiter,
    private val photoReadyTimeoutMs: Long = DEFAULT_PHOTO_READY_TIMEOUT_MS,
    private val settleDelayMs: Long = DEFAULT_SETTLE_DELAY_MS,
    private val transferTimeoutMs: Long = DEFAULT_TRANSFER_TIMEOUT_MS,
    private val chunkTimeoutMs: Long = DEFAULT_CHUNK_TIMEOUT_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HeyCyanThumbnailCapture {
    private val awaitingPhotoReady = AtomicBoolean(false)
    val isAwaitingPhotoReady: Boolean get() = awaitingPhotoReady.get()
    companion object {
        const val DEFAULT_PHOTO_READY_TIMEOUT_MS = 5_000L
        const val DEFAULT_SETTLE_DELAY_MS = 200L
        const val DEFAULT_TRANSFER_TIMEOUT_MS = 14_000L
        const val DEFAULT_CHUNK_TIMEOUT_MS = 2_000L
        private const val MAX_CHUNK_RETRIES = 3
        private val JPEG_START_OF_FRAME_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
        )
    }

    override suspend fun capture(quality: Int): Result<HeyCyanCapturedThumbnail> =
        captureThumbnail(quality)

    suspend fun captureThumbnail(quality: Int = 3): Result<HeyCyanCapturedThumbnail> {
        require(quality in 0..5) { "AI thumbnail quality must be in 0..5" }
        val lease = resourceArbiter.tryAcquire(
            operation = "captureThumbnail",
            resources = setOf(HeyCyanResource.CAMERA, HeyCyanResource.BLE_BULK_TRANSFER),
        ) ?: return Result.failure(
            IllegalStateException("HeyCyan glasses are busy with a conflicting operation"),
        )

        return try {
            val thumbnail = executeCaptureFlow(quality)
            Result.success(thumbnail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            lease.release()
        }
    }

    /**
     * Receives a thumbnail for a photo-ready event that has already been observed,
     * such as a hardware-originated Chat-AI interaction. No second camera command is sent.
     */
    suspend fun receivePhotoReadyThumbnail(
        quality: Int = 3,
        photoReadyAtMs: Long = System.currentTimeMillis(),
    ): Result<HeyCyanCapturedThumbnail> {
        require(quality in 0..5) { "AI thumbnail quality must be in 0..5" }
        val lease = resourceArbiter.tryAcquire(
            operation = "receivePhotoReadyThumbnail",
            resources = setOf(HeyCyanResource.CAMERA, HeyCyanResource.BLE_BULK_TRANSFER),
        ) ?: return Result.failure(IllegalStateException("HeyCyan glasses are busy with a conflicting operation"))
        return try {
            if (settleDelayMs > 0) delay(settleDelayMs)
            val firstChunkAtMs = System.currentTimeMillis()
            Result.success(buildCapturedThumbnail(
                bytes = receiveThumbnailChunks(),
                quality = quality,
                commandAtMs = null,
                photoReadyAtMs = photoReadyAtMs,
                firstChunkAtMs = firstChunkAtMs,
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            lease.release()
        }
    }

    private suspend fun executeCaptureFlow(quality: Int): HeyCyanCapturedThumbnail = coroutineScope {
        val photoReadyDeferred = CompletableDeferred<Long>()
        val listenerJob = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            incomingEvents.filter { it is HeyCyanEvent.AiImageRecognition }.collect {
                if (!photoReadyDeferred.isCompleted) {
                    photoReadyDeferred.complete(System.currentTimeMillis())
                }
            }
        }

        try {
            // 1. Send AI thumbnail capture command
            val commandAtMs = System.currentTimeMillis()
            awaitingPhotoReady.set(true)
            val commandBytes = HeyCyanCommand.aiThumbnail(quality)
            val sendResult = commandTransport.send(commandBytes)
            check(sendResult.isSuccess) {
                "Failed to send AI thumbnail command to glasses: ${sendResult.exceptionOrNull()?.message}"
            }

            // 2. Await photo-ready notification
            val photoReadyTime = withTimeoutOrNull(photoReadyTimeoutMs) {
                photoReadyDeferred.await()
            } ?: throw IllegalStateException("Glasses did not confirm a fresh photo within ${photoReadyTimeoutMs}ms")

            // 3. Settle delay
            if (settleDelayMs > 0) {
                delay(settleDelayMs)
            }

            // 4. Chunk reception pipeline
            val firstChunkAtMs = System.currentTimeMillis()
            val assembledBytes = receiveThumbnailChunks()

            // 5. Verify JPEG payload
            buildCapturedThumbnail(
                bytes = assembledBytes,
                quality = quality,
                commandAtMs = commandAtMs,
                photoReadyAtMs = photoReadyTime,
                firstChunkAtMs = firstChunkAtMs,
            )
        } finally {
            awaitingPhotoReady.set(false)
            listenerJob.cancel()
        }
    }

    private suspend fun receiveThumbnailChunks(): ByteArray = coroutineScope {
        val assembler = HeyCyanThumbnailAssembler()
        val frameAssembler = HeyCyanLargeFrameAssembler()
        val chunkDeferred = CompletableDeferred<ByteArray>()
        val lastProgressAt = AtomicLong(System.currentTimeMillis())

        val chunkCollectorJob = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            incomingRawFrames.collect { rawBytes ->
                for (frame in frameAssembler.accept(rawBytes)) {
                    val chunk = HeyCyanThumbnailTransport.decodeChunk(frame) ?: continue
                    lastProgressAt.set(System.currentTimeMillis())
                    when (val status = assembler.addChunk(chunk)) {
                        is HeyCyanThumbnailAssembler.AssemblyStatus.Completed -> {
                            if (!chunkDeferred.isCompleted) {
                                chunkDeferred.complete(status.bytes)
                            }
                        }
                        is HeyCyanThumbnailAssembler.AssemblyStatus.Failed -> {
                            if (!chunkDeferred.isCompleted) {
                                chunkDeferred.completeExceptionally(
                                    IllegalStateException("Thumbnail assembly failed: ${status.reason}"),
                                )
                            }
                        }
                        is HeyCyanThumbnailAssembler.AssemblyStatus.InProgress -> {
                            // Request next needed chunk
                            commandTransport.send(
                                HeyCyanThumbnailTransport.buildChunkRequest(status.nextNeededIndex),
                            ).getOrThrow()
                        }
                    }
                }
            }
        }

        var requestedIndex = 0
        var retriesForCurrentIndex = 0
        val transferStartTime = System.currentTimeMillis()

        try {
            // Request initial chunk (index 0)
            commandTransport.send(HeyCyanThumbnailTransport.buildChunkRequest(0)).getOrThrow()

            while (!chunkDeferred.isCompleted) {
                val elapsed = System.currentTimeMillis() - transferStartTime
                val remainingTransferTime = (transferTimeoutMs - elapsed).coerceAtLeast(0L)
                if (remainingTransferTime <= 0L) {
                    throw IllegalStateException("Glasses thumbnail transfer timed out after ${transferTimeoutMs}ms")
                }

                val result = withTimeoutOrNull(minOf(chunkTimeoutMs, remainingTransferTime)) {
                    chunkDeferred.await()
                }

                if (result != null) {
                    return@coroutineScope result
                }

                // A busy multi-chunk transfer is healthy even though the final-image
                // deferred is not complete yet. Retry accounting applies only to an
                // actually stalled chunk, not total transfer duration.
                if (System.currentTimeMillis() - lastProgressAt.get() < chunkTimeoutMs) {
                    retriesForCurrentIndex = 0
                    continue
                }

                // If chunk timed out, check next needed index and retry
                val nextNeeded = assembler.getNextNeededIndex()
                if (nextNeeded >= 0) {
                    if (nextNeeded == requestedIndex) {
                        retriesForCurrentIndex++
                        if (retriesForCurrentIndex > MAX_CHUNK_RETRIES) {
                            throw IllegalStateException("Exceeded max retries ($MAX_CHUNK_RETRIES) requesting thumbnail chunk $nextNeeded")
                        }
                    } else {
                        requestedIndex = nextNeeded
                        retriesForCurrentIndex = 0
                    }
                    commandTransport.send(HeyCyanThumbnailTransport.buildChunkRequest(nextNeeded)).getOrThrow()
                }
            }

            chunkDeferred.await()
        } finally {
            chunkCollectorJob.cancel()
        }
    }

    private fun buildCapturedThumbnail(
        bytes: ByteArray,
        quality: Int,
        commandAtMs: Long?,
        photoReadyAtMs: Long,
        firstChunkAtMs: Long?,
    ): HeyCyanCapturedThumbnail {
        validateJpeg(bytes)
        val (width, height) = extractImageDimensions(bytes)
        check(width > 0 && height > 0) { "JPEG decoder could not determine thumbnail dimensions" }
        return HeyCyanCapturedThumbnail(
            bytes = bytes,
            quality = quality,
            commandAtMs = commandAtMs,
            firstChunkAtMs = firstChunkAtMs,
            photoReadyAtMs = photoReadyAtMs,
            completedAtMs = System.currentTimeMillis(),
            width = width,
            height = height,
        )
    }

    private fun validateJpeg(bytes: ByteArray) {
        check(bytes.size >= 1024) { "Thumbnail image too small (${bytes.size} bytes)" }
        // JPEG SOI (Start Of Image) marker: 0xFF, 0xD8
        check(
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte(),
        ) { "Invalid JPEG magic header: expected FF D8" }
        // JPEG EOI (End Of Image) marker: 0xFF, 0xD9
        val last = bytes.size - 1
        check(
            bytes[last - 1] == 0xFF.toByte() && bytes[last] == 0xD9.toByte(),
        ) { "Invalid JPEG termination: missing FF D9 end-of-image marker" }
    }

    private fun extractImageDimensions(bytes: ByteArray): Pair<Int, Int> {
        val options = runCatching {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            opts
        }.getOrNull()
        val width = options?.outWidth ?: -1
        val height = options?.outHeight ?: -1
        return if (width > 0 && height > 0) Pair(width, height) else extractJpegFrameDimensions(bytes)
    }

    /** Reads SOF dimensions without decoding pixels, which also keeps validation deterministic in JVM tests. */
    private fun extractJpegFrameDimensions(bytes: ByteArray): Pair<Int, Int> {
        var index = 2
        while (index + 8 < bytes.size) {
            if (bytes[index] != 0xFF.toByte()) {
                index++
                continue
            }
            val marker = bytes[index + 1].toInt() and 0xFF
            if (marker in JPEG_START_OF_FRAME_MARKERS) {
                val height = ((bytes[index + 5].toInt() and 0xFF) shl 8) or
                    (bytes[index + 6].toInt() and 0xFF)
                val width = ((bytes[index + 7].toInt() and 0xFF) shl 8) or
                    (bytes[index + 8].toInt() and 0xFF)
                return width to height
            }
            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7) {
                index += 2
                continue
            }
            if (index + 3 >= bytes.size) break
            val segmentLength = ((bytes[index + 2].toInt() and 0xFF) shl 8) or
                (bytes[index + 3].toInt() and 0xFF)
            if (segmentLength < 2) break
            index += segmentLength + 2
        }
        return -1 to -1
    }

}
