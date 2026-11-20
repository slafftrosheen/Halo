package com.halo.app.capture

import com.halo.drivers.heycyan.thumbnail.HeyCyanCapturedThumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HeyCyanThumbnailDeliveryTest {
    private val thumbnail = HeyCyanCapturedThumbnail(byteArrayOf(1), 3, null,
        photoReadyAtMs = 1, completedAtMs = 2, width = 1, height = 1)

    @Test fun rejectedReviewIsAnOperationFailureRatherThanAnUncaughtHardwareEvent() = runBlocking {
        val rejection = IllegalStateException("Review already open")
        val result = deliverHeyCyanThumbnail(Result.success(thumbnail)) { throw rejection }
        assertSame(rejection, result.exceptionOrNull())
    }

    @Test fun failedTransferNeverEntersReview() = runBlocking {
        val failure = IllegalStateException("Transfer unavailable")
        val result = deliverHeyCyanThumbnail(Result.failure(failure)) { fail("No image to review") }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun acceptedTransferRetainsExactThumbnailAndCancellationPropagates() = runBlocking {
        var received: HeyCyanCapturedThumbnail? = null
        assertSame(thumbnail, deliverHeyCyanThumbnail(Result.success(thumbnail)) { received = it }.getOrThrow())
        assertSame(thumbnail, received)
        val cancelled = CancellationException("Review cancelled")
        try {
            deliverHeyCyanThumbnail(Result.success(thumbnail)) { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
}
