package com.halo.app.ui.screens.settings

import com.halo.app.speech.diagnostics.SpeechDiagnosticRunner
import kotlinx.coroutines.*

/** Selected-model convenience action using the same composition runner as Speech Lab. */
class SpeechRecognitionProbe(private val runner: SpeechDiagnosticRunner) {
    private var finish: CompletableDeferred<Unit>? = null
    private var job: Job? = null
    fun finishCapture() { finish?.complete(Unit) }
    fun cancel() { job?.cancel(); finish?.complete(Unit) }
    suspend fun run(onRecording: (Boolean) -> Unit): String {
        job = currentCoroutineContext()[Job]
        val signal = CompletableDeferred<Unit>().also { finish = it }
        var session: com.halo.app.speech.diagnostics.SpeechDiagnosticSession? = null
        try {
            session = runner.captureSession(signal)
            onRecording(false)
            runner.recognizeSelected(session)
            return session.summary(includeTranscripts = true)
        } finally { session?.close(); finish = null; job = null }
    }
}
val SpeechTestReferences = com.halo.app.speech.diagnostics.SpeechDiagnosticReferences
