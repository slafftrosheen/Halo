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
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class AndroidSpeechRecognitionProvider(
    private val context: Context,
    private val getLocale: () -> String = { Locale.getDefault().toLanguageTag() }
) : LiveSpeechRecognitionProvider {

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var listening = false
    @Volatile private var cancellationRequested = false
    private val sessionGeneration = AtomicLong(0L)

    private val configuredLocale: String
        get() = getLocale()

    override fun startListening(): Flow<RecognitionEvent> = callbackFlow {
        val generation = sessionGeneration.incrementAndGet()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun cleanup(expectedGeneration: Long) {
            if (sessionGeneration.get() != expectedGeneration) return
            listening = false
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }

        handler.post {
            if (sessionGeneration.get() != generation) return@post
            if (listening) cleanup(generation)
            listening = true
            cancellationRequested = false

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, configuredLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                private fun isCurrent(): Boolean = sessionGeneration.get() == generation

                override fun onReadyForSpeech(params: Bundle?) {
                    if (isCurrent()) trySend(RecognitionEvent.Ready)
                }

                override fun onBeginningOfSpeech() {
                    if (isCurrent()) trySend(RecognitionEvent.Listening)
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (isCurrent()) trySend(RecognitionEvent.Stopped)
                }

                override fun onError(error: Int) {
                    if (!isCurrent()) return
                    listening = false

                    if (cancellationRequested || error == SpeechRecognizer.ERROR_CLIENT) {
                        close()
                        return
                    }

                    trySend(RecognitionEvent.Error("Speech recognition failed with error code: $error"))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrent()) return
                    listening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        trySend(RecognitionEvent.Final(RecognitionResult(text, configuredLocale, 1.0f)))
                    } else {
                        trySend(RecognitionEvent.Error("Speech recognition returned no text"))
                    }
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrent()) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) trySend(RecognitionEvent.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                recognizer.startListening(intent)
            } catch (error: RuntimeException) {
                listening = false
                trySend(RecognitionEvent.Error("Unable to start speech recognition: ${error.message ?: error::class.simpleName}"))
                recognizer.destroy()
                speechRecognizer = null
                close()
            }
        }

        awaitClose {
            handler.post {
                cleanup(generation)
            }
        }
    }

    override suspend fun stopListening() {
        cancellationRequested = true
        val generation = sessionGeneration.get()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (sessionGeneration.get() != generation) return@post
            listening = false
            speechRecognizer?.stopListening()
        }
    }

    override suspend fun close() {
        cancellationRequested = true
        val generation = sessionGeneration.incrementAndGet()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (sessionGeneration.get() != generation) return@post
            listening = false
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override suspend fun health(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
