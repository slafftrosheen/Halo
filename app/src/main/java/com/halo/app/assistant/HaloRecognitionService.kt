package com.halo.app.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.halo.app.di.ManualDependencyGraph
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import kotlinx.coroutines.*

/** Bounded platform STT through the same microphone owner, selected model, and quality gate.
 * This path publishes no production speech events, so an external SpeechRecognizer
 * request cannot accidentally start a Halo cognitive turn. */
class HaloRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Request(val callback: Callback, val finish: CompletableDeferred<Unit> = CompletableDeferred(),
        var job: Job? = null)
    private var active: Request? = null

    override fun onCreate() {
        super.onCreate()
        ManualDependencyGraph.init(applicationContext)
        AssistantRuntimeStateStore.recognitionServiceCreated()
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        AssistantRuntimeStateStore.recognitionRequested(recognizerIntent?.action)
        if (active != null) { listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS); return
        }
        // External audio and segmented/continuous sessions need a different ownership contract.
        if (recognizerIntent?.hasExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE) == true ||
            recognizerIntent?.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION) == true) {
            listener.error(SpeechRecognizer.ERROR_CLIENT); return
        }
        val request = Request(listener)
        active = request
        request.job = scope.launch {
            var sample: com.halo.app.speech.diagnostics.SpeechDiagnosticSession? = null
            try {
                withContext(Dispatchers.IO) { ManualDependencyGraph.runtime.start() }
                val runner = ManualDependencyGraph.speechDiagnosticRunner
                val requestedLanguage = recognizerIntent?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                if (requestedLanguage != null && !requestedLanguage.substringBefore('-').equals(runner.language().substringBefore('-'), true)) {
                    fail(request, if (android.os.Build.VERSION.SDK_INT >= 31) SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE else SpeechRecognizer.ERROR_CLIENT)
                    return@launch
                }
                if (!runner.selectedHealth().healthy) { fail(request, SpeechRecognizer.ERROR_SERVER); return@launch }
                listener.readyForSpeech(Bundle())
                sample = runner.captureSession(finish = request.finish, maxDurationMs = 15_000)
                if (active !== request) return@launch
                listener.endOfSpeech()
                val result = runner.recognizeSelected(sample)
                if (active !== request) return@launch
                val recognized = result.recognition
                if (recognized == null) { fail(request, SpeechRecognizer.ERROR_SERVER); return@launch }
                if (result.gate != "Accepted" || recognized.text.isBlank()) { fail(request, SpeechRecognizer.ERROR_NO_MATCH); return@launch }
                active = null
                listener.results(Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(recognized.text))
                    recognized.confidence?.let { putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(it)) }
                })
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: SecurityException) { fail(request, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
              catch (error: Exception) {
                val busy = error.message?.contains("busy", true) == true || error.cause?.message?.contains("busy", true) == true
                fail(request, if (busy) SpeechRecognizer.ERROR_RECOGNIZER_BUSY else SpeechRecognizer.ERROR_AUDIO)
            } finally {
                sample?.close()
                if (active === request) active = null
            }
        }
    }

    private fun fail(request: Request, error: Int) {
        if (active === request) { active = null; request.callback.error(error) }
    }
    override fun onStopListening(listener: Callback) {
        active?.takeIf { it.callback === listener }?.finish?.complete(Unit)
    }
    override fun onCancel(listener: Callback) {
        active?.takeIf { it.callback === listener }?.let { active = null; it.job?.cancel() }
    }
    override fun onDestroy() {
        active?.job?.cancel(); active = null; scope.cancel()
        super.onDestroy()
    }
}
