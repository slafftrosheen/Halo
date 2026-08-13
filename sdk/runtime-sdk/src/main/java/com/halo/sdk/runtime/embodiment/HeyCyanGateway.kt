package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.flow.Flow

/**
 * HeyCyanGateway translates semantic EmbodimentIntents (e.g. CAPTURE_PHOTO)
 * into protocol-specific actions, insulating the Halo cognitive layer from BLE details.
 */
class HeyCyanGateway(private val eventBus: EventBus) : EmbodimentGateway {

    override suspend fun dispatch(intent: EmbodimentIntent) {
        when (intent.kind) {
            IntentKind.CAPTURE_PHOTO -> {
                // Here we would emit the semantic HeyCyan action to the actual BLE layer.
                // For now, this is where the translation happens.
                sendHeyCyanCommand("PHOTO")
            }
            IntentKind.START_VIDEO -> {
                sendHeyCyanCommand("VIDEO_START")
            }
            IntentKind.STOP_VIDEO -> {
                sendHeyCyanCommand("VIDEO_STOP")
            }
            IntentKind.START_AUDIO_RECORDING -> {
                sendHeyCyanCommand("RECORD_START")
            }
            IntentKind.STOP_AUDIO_RECORDING -> {
                sendHeyCyanCommand("RECORD_STOP")
            }
            IntentKind.FETCH_MEDIA_COUNT -> {
                sendHeyCyanCommand("MEDIA_COUNT")
            }
            else -> {
                // Ignore intents not supported by HeyCyan
            }
        }
    }

    private fun sendHeyCyanCommand(command: String) {
        // In a real implementation, this forwards the semantic command string 
        // to the HeyCyan BLE protocol encoder. 
    }
}
