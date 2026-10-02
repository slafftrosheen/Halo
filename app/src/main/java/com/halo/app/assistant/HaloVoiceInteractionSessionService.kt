package com.halo.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.halo.app.di.ManualDependencyGraph
import com.halo.host.android.assistant.AssistantRuntimeStateStore

class HaloVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onCreate() {
        super.onCreate()
        AssistantRuntimeStateStore.sessionServiceCreated()
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AssistantRuntimeStateStore.sessionCreated()
        android.util.Log.i("HaloAssistant", "HALO_ASSISTANT_SESSION_CREATED")
        return HaloVoiceInteractionSession(
            this,
            ManualDependencyGraph.assistantInvocationCoordinator,
            applicationScope = ManualDependencyGraph.infra.scope,
        )
    }
}
