package com.halo.app.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.halo.host.android.assistant.AssistantRuntimeStateStore

/**
 * Recognition-service endpoint required by Android's voice-interaction-service contract.
 *
 * Halo's interactive session owns its microphone and offline STT lifecycle. If Android invokes
 * this separate platform recognizer endpoint, fail promptly instead of leaving the system bound to
 * a request that can never complete or competing with Halo for the microphone.
 */
class HaloRecognitionService : RecognitionService() {
    override fun onCreate() {
        super.onCreate()
        AssistantRuntimeStateStore.recognitionServiceCreated()
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        AssistantRuntimeStateStore.recognitionRequested(recognizerIntent?.action)
        // This endpoint exists for Android VoiceInteractionService compatibility. Halo's
        // VoiceInteractionSession performs STT through AudioPerceptionEngine, so an independent
        // platform recognition request is rejected before it can contend for the microphone.
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}
