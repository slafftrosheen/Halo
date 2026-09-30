package com.halo.app.assistant

import android.graphics.Bitmap
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.AssistantInvocationEnded
import com.halo.sdk.runtime.AssistantInvocationStarted
import com.halo.sdk.runtime.Freshness
import com.halo.sdk.runtime.HostInvocation
import com.halo.sdk.runtime.ImageObservationPayload
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.ObservationUpdated
import com.halo.sdk.runtime.ScreenContextCaptured
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechInputTurn
import android.content.Context
import com.halo.app.audio.AndroidInvocationAudioResolver
import com.halo.host.android.assistant.AssistantState
import com.halo.sdk.runtime.StopListening
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AssistantRuntimeBridge {
    suspend fun begin(invocation: HostInvocation)
    suspend fun attachScreen(interactionId: String, snapshot: ScreenContextSnapshot)
    suspend fun attachScreenshot(interactionId: String, bitmap: Bitmap)
    suspend fun startListening(interactionId: String, inputDeviceId: Int?): Boolean
    suspend fun hideAssistantSurface(interactionId: String)
    suspend fun cancelAssistantInteraction(interactionId: String, reason: String)
}

class HaloAssistantRuntimeBridge(private val context: Context) : AssistantRuntimeBridge {
    private val audioResolver = AndroidInvocationAudioResolver(context)

    override suspend fun begin(invocation: HostInvocation) {
        withContext(Dispatchers.IO) { ManualDependencyGraph.runtime.start() }
        ManualDependencyGraph.eventBus.publish(AssistantInvocationStarted(invocation))
    }

    override suspend fun attachScreen(interactionId: String, snapshot: ScreenContextSnapshot) {
        ManualDependencyGraph.assistantContextRepository.put(interactionId, snapshot)
        ManualDependencyGraph.eventBus.publish(ScreenContextCaptured(interactionId, snapshot))
    }

    override suspend fun attachScreenshot(interactionId: String, bitmap: Bitmap) {
        val screen = ManualDependencyGraph.assistantContextRepository.screenFor(interactionId)
            ?: throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.POLICY_BLOCKED)
        if (screen.packageName == null || com.halo.host.android.accessibility.AccessibilitySafetyPolicy.isPackageBlocked(screen.packageName) ||
            screen.omissionCounts.values.any { it > 0 } ||
            !ManualDependencyGraph.storage.getBoolean("policy_screen_image_fallback", false)) {
            throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.POLICY_BLOCKED)
        }
        if (screen.inspectability == com.halo.sdk.runtime.UiInspectability.INSPECTABLE) return
        if (com.halo.app.capture.inspectMultimodal(ManualDependencyGraph.aiProviderRouter).target == null) {
            throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.VISION_MODEL_UNAVAILABLE)
        }
        val prepared = withContext(Dispatchers.Default) { bitmap.limitLongestEdge(MAX_SCREENSHOT_EDGE_PX) }
        try {
            val bytes = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().use { output ->
                    check(prepared.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                        "Unable to encode assistant screenshot"
                    }
                    output.toByteArray()
                }
            }
            val capturedAtMs = System.currentTimeMillis()
            val mediaRef = ManualDependencyGraph.mediaAssetStore.ingest(
                com.halo.sdk.runtime.multimodal.MediaAssetContent(bytes, "image/jpeg"),
                com.halo.sdk.runtime.multimodal.MediaProvenance.SCREEN_CAPTURE, interactionId,
            )
            ManualDependencyGraph.assistantContextRepository.putImage(
                interactionId,
                com.halo.runtime.cognition.context.ImageContext(
                    mediaRef = mediaRef,
                    mimeType = "image/jpeg",
                    timestampMs = capturedAtMs,
                    assetId = "assist:$interactionId:$capturedAtMs",
                    interactionId = interactionId,
                    originDeviceId = "host:primary",
                    source = com.halo.runtime.cognition.context.ContextSource.AssistantScreenshot,
                    trust = com.halo.runtime.cognition.context.ContextTrust.EXTERNAL_UNTRUSTED,
                    freshness = com.halo.sdk.runtime.VisualFreshness.CURRENT_VIEW,
                ),
            )
            ManualDependencyGraph.eventBus.publish(
                ObservationUpdated(
                    observation = Observation(
                        modality = Modality.IMAGE,
                        source = Source.OS,
                        payload = ImageObservationPayload(mediaRef = mediaRef),
                        origin = ObservationOrigin(ObservationSource.AndroidAssist, null),
                        sourceDeviceId = null,
                        freshness = Freshness.FRESH,
                        timestamp = capturedAtMs,
                        metadata = mapOf(
                            "captureKind" to "android_assist_screenshot",
                            "width" to prepared.width,
                            "height" to prepared.height,
                            "trust" to "UNTRUSTED_CONTENT",
                            "persistence" to "transient",
                        ),
                        interactionId = interactionId,
                    )
                )
            )
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    override suspend fun startListening(interactionId: String, inputDeviceId: Int?): Boolean =
        withContext(Dispatchers.IO) {
            val route = audioResolver.resolve(inputDeviceId)
            ManualDependencyGraph.hostGraph.androidInteractionAudioManager.prepareInteractionRoute(interactionId, route)
            val started = ManualDependencyGraph.audioPerceptionEngine.startCapture(
                SpeechInputTurn(
                    captureId = interactionId,
                    route = route,
                    source = if (route is SpeechInputRoute.Phone) ObservationSource.AndroidPhoneMic else ObservationSource.BluetoothHeadset,
                )
            )
            if (started) {
                com.halo.host.android.assistant.AssistantRuntimeStateStore.capture(
                    interactionId,
                    AssistantState.LISTENING,
                    when (route) {
                        SpeechInputRoute.Phone -> "phone"
                        is SpeechInputRoute.Device -> "device:${route.deviceId ?: route.platformEndpointId ?: "unknown"}"
                    },
                )
            }
            started
        }

    override suspend fun hideAssistantSurface(interactionId: String) {
        // Dismissing the system assistant must release its microphone capture even when the
        // cognitive turn has not started yet. Otherwise a hidden session can keep the recorder
        // and prevent the next assistant invocation from listening.
        if (ManualDependencyGraph.audioPerceptionEngine.inputStatus.value.turn?.captureId == interactionId) {
            ManualDependencyGraph.commandBus.dispatch(StopListening)
        }
        ManualDependencyGraph.eventBus.publish(AssistantInvocationEnded(interactionId, "hidden"))
    }

    override suspend fun cancelAssistantInteraction(interactionId: String, reason: String) {
        if (ManualDependencyGraph.audioPerceptionEngine.inputStatus.value.turn?.captureId == interactionId) {
            ManualDependencyGraph.commandBus.dispatch(StopListening)
        }
        ManualDependencyGraph.cognitiveSession.cancel()
        ManualDependencyGraph.eventBus.publish(AssistantInvocationEnded(interactionId, reason))
    }

    private fun Bitmap.limitLongestEdge(limit: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= limit) return this
        val scale = limit.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object { const val MAX_SCREENSHOT_EDGE_PX = 2_048 }
}
