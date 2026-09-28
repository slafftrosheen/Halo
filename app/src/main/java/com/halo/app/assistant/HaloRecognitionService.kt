package com.halo.app.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Recognition-service endpoint required by Android's voice-interaction-service contract.
 *
 * Halo's interactive session owns its microphone and offline STT lifecycle. If Android invokes
 * this separate platform recognizer endpoint, fail promptly instead of leaving the system bound to
 * a request that can never complete or competing with Halo for the microphone.
 */
class HaloRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}
