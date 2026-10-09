package com.halo.app.assistant

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import com.halo.host.android.assistant.AssistantState
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.InvocationSource
import com.halo.sdk.runtime.ScreenContextSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AssistantInvocationCoordinatorTest {
    private class Bus : EventBus {
        val stream = MutableSharedFlow<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { stream.emit(event) }
        override fun events(): Flow<HaloEvent> = stream
        @Suppress("UNCHECKED_CAST")
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> =
            stream.filter { type.isInstance(it) } as Flow<T>
    }

    private class Bridge : AssistantRuntimeBridge {
        var rejectFirst = true
        val cancelled = mutableListOf<String>()
        val started = mutableListOf<String>()
        override suspend fun begin(invocation: HostInvocation) {
            if (rejectFirst) {
                rejectFirst = false
                throw IllegalStateException("simulated native start failure")
            }
            started.add(invocation.interactionId)
        }
        override suspend fun attachScreen(interactionId: String, snapshot: ScreenContextSnapshot) = Unit
        override suspend fun attachScreenshot(interactionId: String, bitmap: Bitmap) = Unit
        override suspend fun startListening(interactionId: String, inputDeviceId: Int?) = true
        override suspend fun hideAssistantSurface(interactionId: String) = Unit
        override suspend fun cancelAssistantInteraction(interactionId: String, reason: String) {
            cancelled.add("$interactionId:$reason")
        }
    }

    private fun invocation(id: String) = HostInvocation(
        interactionId = id, source = InvocationSource.ANDROID_ASSISTANT,
        invokedAtMs = System.currentTimeMillis(),
        requestedAssistContext = false, requestedScreenshot = false,
    )

    @Test fun failedSpeechPlaybackLeavesPendingReviewActionable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bus = Bus()
        val bridge = Bridge().apply { rejectFirst = false }
        val coordinator = AssistantInvocationCoordinator(
            ApplicationProvider.getApplicationContext<Context>(), bus, scope, bridge,
        )
        try {
            coordinator.begin(invocation("pending-voice"))
            bus.publish(AssistantResponsePublished(
                text = "Review the action", delivery = ResponseDelivery.SPEECH_AND_VISUAL,
                transactionId = "pending-turn", interactionId = "pending-voice",
                continuationPending = true
            ))
            bus.publish(TtsRequested(
                text = "Review the action", transactionId = "pending-turn",
                interactionId = "pending-voice", target = SpeechOutputTarget.Phone
            ))
            bus.publish(TtsFailed(
                transactionId = "pending-turn", interactionId = "pending-voice",
                reason = "NO_OUTPUT_ROUTE"
            ))
            assertEquals(AssistantState.WAITING_FOR_INPUT, AssistantRuntimeStateStore.state.value.state)
            assertEquals("NO_OUTPUT_ROUTE", AssistantRuntimeStateStore.state.value.lastFailure)
            coordinator.hideAssistantSurface("pending-voice")
            assertNull(AssistantRuntimeStateStore.state.value.interactionId)
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedStartCleansUpAndDoesNotSupersedeSuccessfulRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = Bridge()
        val coordinator = AssistantInvocationCoordinator(
            ApplicationProvider.getApplicationContext<Context>(), Bus(), scope, bridge,
        )
        try {
            try {
                coordinator.begin(invocation("failed"))
                fail("The simulated start must fail")
            } catch (expected: IllegalStateException) {
                assertEquals("simulated native start failure", expected.message)
            }
            assertEquals(listOf("failed:start_failed"), bridge.cancelled)
            assertNull(AssistantRuntimeStateStore.state.value.interactionId)

            coordinator.begin(invocation("ok"))
            assertEquals(listOf("ok"), bridge.started)
            assertEquals(listOf("failed:start_failed"), bridge.cancelled)
            assertEquals("ok", AssistantRuntimeStateStore.state.value.interactionId)
            coordinator.cancelAssistantInteraction("ok", "test")
        } finally {
            scope.cancel()
        }
    }
}
