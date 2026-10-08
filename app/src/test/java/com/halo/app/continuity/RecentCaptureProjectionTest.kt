package com.halo.app.continuity
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecentCaptureProjectionTest {
    private class Events : EventBus {
        val flow = MutableSharedFlow<HaloEvent>()
        override fun events(): Flow<HaloEvent> = flow
        override suspend fun publish(event: HaloEvent) { flow.emit(event) }
        override fun <T: HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
    @Test fun exactWorkspaceIdentityExpiresAndDismissesWithoutMediaAccess() = runTest {
        val events = Events(); val projection = RecentCaptureProjection(events, backgroundScope) { testScheduler.currentTime }
        runCurrent()
        events.publish(ActionReceipt(operationId = "capture", category = "Vision", title = "Image received", stage = "Completed",
            timestamp = testScheduler.currentTime, objectId = "exact-note", sourceDeviceId = "private-device", detail = "private path must not be projected"))
        runCurrent()
        assertEquals(RecentCapture("exact-note", "Wearable", "Photo", 0), projection.recent.value)
        advanceTimeBy(300_001); runCurrent(); assertNull(projection.recent.value)
        events.publish(ActionReceipt(operationId = "capture2", category = "Vision", title = "Image received", stage = "Completed", detail = "saved", timestamp = testScheduler.currentTime, objectId = "other"))
        runCurrent(); assertEquals("other", projection.recent.value!!.objectId)
        projection.dismiss(); assertNull(projection.recent.value)
    }
}
