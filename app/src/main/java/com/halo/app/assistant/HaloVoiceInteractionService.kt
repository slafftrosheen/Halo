package com.halo.app.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log
import com.halo.host.android.assistant.AssistantRuntimeStateStore

/** Lightweight process entry point; interaction work belongs to the session service. */
class HaloVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        AssistantRuntimeStateStore.serviceReady(true)
        Log.i(TAG, "HALO_ASSISTANT_READY component=$packageName/${javaClass.name}")
    }

    override fun onShutdown() {
        AssistantRuntimeStateStore.serviceReady(false)
        Log.i(TAG, "HALO_ASSISTANT_SHUTDOWN component=$packageName/${javaClass.name}")
        super.onShutdown()
    }

    companion object {
        private const val TAG = "HaloAssistant"
    }
}
