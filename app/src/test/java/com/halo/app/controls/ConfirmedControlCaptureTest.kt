package com.halo.app.controls
import com.halo.sdk.host.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmedControlCaptureTest {
    @Test fun questionWaitsForConfirmedStoredCaptureAndNeverInfers() = runTest {
        val receipt = CompletableDeferred<String>(); val order = mutableListOf<String>()
        val job = async { confirmedControlCapture(AccessoryAction.CaptureAndAsk,
            captureAndSave = { order += "capture"; receipt.await() },
            verifiedVision = { order += "vision"; true },
            attachForQuestion = { id -> assertEquals("saved-exact-image", id); order += "attach"; true },
            openQuestion = { order += "open"; AccessoryActionResult(AccessoryActionOutcome.OPENED_FOR_REVIEW, "Ask") }) }
        runCurrent(); assertEquals(listOf("capture"), order)
        receipt.complete("saved-exact-image"); assertEquals(AccessoryActionOutcome.OPENED_FOR_REVIEW, job.await().outcome)
        assertEquals(listOf("capture", "vision", "attach", "open"), order)
    }
    @Test fun missingVisionPreservesSavedPhotoWithoutAttachingOrOpening() = runTest {
        var saved = false
        val result = confirmedControlCapture(AccessoryAction.CaptureAndAsk,
            captureAndSave = { saved = true; "artifact" }, verifiedVision = { false },
            attachForQuestion = { error("must not attach") }, openQuestion = { error("must not infer or open") })
        assertTrue(saved); assertEquals(AccessoryActionOutcome.UNAVAILABLE, result.outcome)
    }
    @Test fun failedCaptureDoesNotReachQuestionOrVision() = runTest {
        assertEquals(AccessoryActionOutcome.FAILED, confirmedControlCapture<String>(AccessoryAction.CaptureAndAsk,
            captureAndSave = { null }, verifiedVision = { error("no image") }, attachForQuestion = { error("no image") },
            openQuestion = { error("no image") }).outcome)
    }
    @Test fun photoOnlyDoesNotInvokeVisionOrQuestion() = runTest {
        assertEquals(AccessoryActionOutcome.EXECUTED, confirmedControlCapture(AccessoryAction.TakePhoto,
            captureAndSave = { "stored" }, verifiedVision = { error("no vision request") }, attachForQuestion = { error("no attachment") },
            openQuestion = { error("no question") }).outcome)
    }
}
