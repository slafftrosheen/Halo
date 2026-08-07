package com.halo.providers.android.stt

import android.content.Context
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.provider.SpeechRecognitionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubAndroidSpeechRecognitionProvider(
    private val context: Context
) : SpeechRecognitionProvider {

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
        throw NotImplementedError("Android SpeechRecognizer cannot consume raw PCM.")
    }

    override fun stream(frames: Flow<com.halo.sdk.host.AudioFrame>): Flow<com.halo.sdk.provider.RecognitionEvent> {
        return flow {
            throw NotImplementedError("Android SpeechRecognizer cannot stream raw PCM.")
        }
    }

    override suspend fun health(): Boolean {
        return true
    }
}
