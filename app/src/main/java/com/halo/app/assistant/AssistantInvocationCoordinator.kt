package com.halo.app.assistant

import android.content.Context
import android.graphics.Bitmap
import com.halo.host.android.assistant.AssistantCaptureState
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechRecognized
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsCompleted
import com.halo.sdk.runtime.TtsCancelled
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.TtsStarted
import com.halo.sdk.runtime.ScreenContextSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single Android adapter into Halo's existing speech/cognition runtime. Both the system
 * VoiceInteractionSession and ACTION_ASSIST activity fallback enter here.
 */
class AssistantInvocationCoordinator(
    context: Context,
    private val eventBus: EventBus,
    scope: CoroutineScope,
    private val delegate: AssistantRuntimeBridge = HaloAssistantRuntimeBridge(context.applicationContext),
) : AssistantRuntimeBridge {
    private val invocationMutex = Mutex()
    private var activeInteractionId: String? = null

    init {
        scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is SpeechRecognized -> event.input?.captureId?.let { interactionId ->
                        AssistantRuntimeStateStore.capture(interactionId, AssistantCaptureState.TRANSCRIBING)
                    }
                    is CognitiveRequestStarted -> AssistantRuntimeStateStore.capture(
                        event.interactionId,
                        AssistantCaptureState.INFERENCE_RUNNING,
                    )
                    is TtsRequested -> {
                        AssistantRuntimeStateStore.capture(event.interactionId, AssistantCaptureState.RESPONDING)
                        AssistantRuntimeStateStore.outputRoute(event.interactionId, event.target.diagnosticLabel())
                    }
                    is TtsStarted -> AssistantRuntimeStateStore.outputRoute(
                        event.interactionId,
                        event.target.diagnosticLabel(),
                    )
                    is TtsCompleted -> AssistantRuntimeStateStore.responseCompleted(event.interactionId)
                    is TtsCancelled -> AssistantRuntimeStateStore.failed(event.interactionId, "Speech response cancelled")
                    is CognitiveFailed -> AssistantRuntimeStateStore.failed(
                        event.interactionId,
                        "${event.stage}: ${event.message}",
                    )
                    is TtsFailed -> AssistantRuntimeStateStore.failed(event.interactionId, event.reason)
                }
            }
        }
    }

    override suspend fun begin(invocation: HostInvocation) {
        invocationMutex.withLock {
            activeInteractionId?.takeIf { it != invocation.interactionId }?.let { previous ->
                delegate.cancelAssistantInteraction(previous, "superseded")
                AssistantRuntimeStateStore.invocationEnded(previous)
            }
            activeInteractionId = invocation.interactionId
            AssistantRuntimeStateStore.invocationStarted(invocation.interactionId, invocation.source.name)
            delegate.begin(invocation)
        }
    }

    override suspend fun attachScreen(interactionId: String, snapshot: ScreenContextSnapshot) =
        delegate.attachScreen(interactionId, snapshot)

    override suspend fun attachScreenshot(interactionId: String, bitmap: Bitmap) =
        delegate.attachScreenshot(interactionId, bitmap)

    override suspend fun startListening(interactionId: String, inputDeviceId: Int?): Boolean {
        val started = delegate.startListening(interactionId, inputDeviceId)
        if (!started) AssistantRuntimeStateStore.failed(interactionId, "Microphone capture did not start")
        return started
    }

    override suspend fun hideAssistantSurface(interactionId: String) {
        delegate.hideAssistantSurface(interactionId)
        finish(interactionId)
    }

    override suspend fun cancelAssistantInteraction(interactionId: String, reason: String) {
        delegate.cancelAssistantInteraction(interactionId, reason)
        finish(interactionId)
    }

    private suspend fun finish(interactionId: String) = invocationMutex.withLock {
        if (activeInteractionId == interactionId) activeInteractionId = null
        AssistantRuntimeStateStore.invocationEnded(interactionId)
    }

    private fun SpeechOutputTarget.diagnosticLabel(): String = when (this) {
        SpeechOutputTarget.Phone -> "phone"
        is SpeechOutputTarget.Device -> "device:${deviceId ?: platformEndpointId ?: "unknown"}"
    }
}
