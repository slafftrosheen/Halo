package com.halo.app.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class HaloVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i("HaloAssistant", "Halo VoiceInteractionSession shown. Flags: $showFlags")
    }

    override fun onHide() {
        super.onHide()
        Log.i("HaloAssistant", "Halo VoiceInteractionSession hidden.")
    }
}
