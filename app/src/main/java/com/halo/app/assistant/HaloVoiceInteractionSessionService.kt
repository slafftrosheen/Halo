package com.halo.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.halo.app.di.ManualDependencyGraph

class HaloVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return HaloVoiceInteractionSession(
            this,
            HaloAssistantRuntimeBridge(),
            applicationScope = ManualDependencyGraph.infra.scope,
        )
    }
}
