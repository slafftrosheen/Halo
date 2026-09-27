package com.halo.app.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/** Lightweight process entry point; interaction work belongs to the session service. */
class HaloVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i(TAG, "HALO_ASSISTANT_SERVICE_READY component=$packageName/${javaClass.name}")
    }

    override fun onShutdown() {
        Log.i(TAG, "HALO_ASSISTANT_SERVICE_SHUTDOWN component=$packageName/${javaClass.name}")
        super.onShutdown()
    }

    companion object {
        private const val TAG = "HaloAssistant"
    }
}
