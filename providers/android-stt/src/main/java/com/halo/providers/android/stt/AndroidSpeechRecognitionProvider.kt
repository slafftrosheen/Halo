package com.halo.providers.android.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.halo.sdk.provider.LiveSpeechRecognitionProvider
import com.halo.sdk.provider.RecognitionEvent
import com.halo.sdk.provider.RecognitionResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidSpeechRecognitionProvider(
    private val context: Context
) : LiveSpeechRecognitionProvider {

    private var speechRecognizer: SpeechRecognizer? = null

    override fun startListening(): Flow<RecognitionEvent> = callbackFlow {
        // Run on main thread (SpeechRecognizer requirement)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun cleanup() {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        
        handler.post {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        trySend(RecognitionEvent.Ready)
                    }
                    override fun onBeginningOfSpeech() {
                        trySend(RecognitionEvent.Listening)
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        trySend(RecognitionEvent.Stopped)
                    }
                    
                    override fun onError(error: Int) {
                        trySend(RecognitionEvent.Error("Speech recognition failed with error code: $error"))
                        close()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        trySend(RecognitionEvent.Final(RecognitionResult(text, "en-US", 1.0f)))
                        close()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            trySend(RecognitionEvent.Partial(text))
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        }

        awaitClose {
            handler.post {
                cleanup()
            }
        }
    }

    override suspend fun stopListening() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            speechRecognizer?.stopListening()
        }
    }

    override suspend fun close() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override suspend fun health(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
