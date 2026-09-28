package com.halo.app.audio

import com.halo.host.android.audio.AndroidCommunicationAudioRouter
import com.halo.host.android.audio.AudioDeviceSelector
import com.halo.host.android.audio.CommunicationAudioLease
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import com.halo.sdk.runtime.SpeechInputRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidInteractionAudioManager(
    private val audioRouter: AndroidCommunicationAudioRouter,
    eventBus: EventBus,
    scope: CoroutineScope
) {
    private var activeLease: CommunicationAudioLease? = null
    private var activeInteractionId: String? = null
    @Volatile var expectedInputDeviceId: Int? = null
        private set

    init {
        scope.launch {
            eventBus.observe(InteractionTurnChanged::class.java).collect { event ->
                val interactionId = event.state.interactionId
                if (interactionId != null && interactionId == activeInteractionId) {
                    when (event.state) {
                        is InteractionTurnState.Completed,
                        is InteractionTurnState.Failed,
                        is InteractionTurnState.Cancelled,
                        is InteractionTurnState.Interrupted -> {
                            activeLease?.release()
                            activeLease = null
                            activeInteractionId = null
                            expectedInputDeviceId = null
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    suspend fun prepareInteractionRoute(interactionId: String, route: SpeechInputRoute) {
        activeLease?.release()
        activeLease = null
        activeInteractionId = null
        expectedInputDeviceId = null
        if (route is SpeechInputRoute.Device && route.kind != AudioDeviceKind.WEARABLE) {
            val selector = AudioDeviceSelector(deviceId = route.platformEndpointId)
            activeLease = audioRouter.acquire(selector, needsInput = true, needsOutput = true).getOrNull()
            activeInteractionId = interactionId
            expectedInputDeviceId = route.platformEndpointId
        }
        // Phone uses the current Android assistant/media route. HeyCyan wearable transport owns
        // its own route; in both cases any prior Android communication lease must be gone.
    }
}
