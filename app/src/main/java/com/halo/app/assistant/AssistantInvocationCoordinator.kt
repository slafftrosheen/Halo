package com.halo.app.assistant

import android.content.Context
import android.graphics.Bitmap
import com.halo.host.android.assistant.AssistantState
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.ResponseDelivery
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    /** An audible pending-action prompt is not evidence that its action completed. */
    private val pendingInputInteractions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is SpeechRecognized -> event.input?.captureId?.let { interactionId ->
                        AssistantRuntimeStateStore.capture(interactionId, AssistantState.TRANSCRIBING)
                    }
                    is CognitiveRequestStarted -> AssistantRuntimeStateStore.capture(
                        event.interactionId,
                        AssistantState.THINKING,
                    )
                    is AssistantResponsePublished -> {
                        event.interactionId?.let { id ->
                            if (event.continuationPending) pendingInputInteractions.add(id)
                            else pendingInputInteractions.remove(id)
                        }
                        if (event.continuationPending) {
                            AssistantRuntimeStateStore.capture(event.interactionId, AssistantState.WAITING_FOR_INPUT)
                        } else if (event.delivery == ResponseDelivery.VISUAL_ONLY) {
                            AssistantRuntimeStateStore.responseCompleted(event.interactionId)
                        }
                    }
                    is TtsRequested -> {
                        AssistantRuntimeStateStore.capture(event.interactionId, AssistantState.RESPONDING)
                        AssistantRuntimeStateStore.outputRoute(event.interactionId, event.target.diagnosticLabel())
                    }
                    is TtsStarted -> AssistantRuntimeStateStore.outputRoute(
                        event.interactionId,
                        event.target.diagnosticLabel(),
                    )
                    is TtsCompleted -> if (event.interactionId?.let(pendingInputInteractions::contains) == true)
                        AssistantRuntimeStateStore.capture(event.interactionId, AssistantState.WAITING_FOR_INPUT)
                        else AssistantRuntimeStateStore.responseCompleted(event.interactionId)
                    is TtsCancelled -> if (event.interactionId?.let(pendingInputInteractions::contains) == true)
                        AssistantRuntimeStateStore.capture(event.interactionId, AssistantState.WAITING_FOR_INPUT)
                        else AssistantRuntimeStateStore.failed(event.interactionId, "Speech response cancelled")
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
                pendingInputInteractions.remove(previous)
                AssistantRuntimeStateStore.invocationEnded(previous)
            }
            activeInteractionId = invocation.interactionId
            AssistantRuntimeStateStore.invocationStarted(invocation.interactionId, invocation.source.name)
            try {
                delegate.begin(invocation)
            } catch (error: Exception) {
                // Startup can fail after the bridge allocated a foreground/microphone lease.
                // Roll back even when the initiating coroutine was cancelled, then allow a
                // subsequent invocation to start without cancelling a phantom predecessor.
                activeInteractionId = null
                try {
                    withContext(NonCancellable) {
                        delegate.cancelAssistantInteraction(invocation.interactionId, "start_failed")
                    }
                } catch (cleanup: Exception) {
                    error.addSuppressed(cleanup)
                }
                AssistantRuntimeStateStore.failed(invocation.interactionId, "Assistant could not start")
                AssistantRuntimeStateStore.invocationEnded(invocation.interactionId)
                throw error
            }
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
        pendingInputInteractions.remove(interactionId)
        if (activeInteractionId == interactionId) activeInteractionId = null
        AssistantRuntimeStateStore.invocationEnded(interactionId)
    }

    private fun SpeechOutputTarget.diagnosticLabel(): String = when (this) {
        SpeechOutputTarget.Phone -> "phone"
        SpeechOutputTarget.HostDefault -> "system-default"
        is SpeechOutputTarget.Device -> "device:${deviceId ?: platformEndpointId ?: "unknown"}"
    }
}
