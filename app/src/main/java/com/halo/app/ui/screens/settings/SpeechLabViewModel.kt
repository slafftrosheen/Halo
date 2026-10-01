package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.speech.diagnostics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechLabState(val busy: Boolean = false, val recording: Boolean = false, val message: String = "",
    val targets: List<SpeechDiagnosticTarget> = emptyList(), val hasSample: Boolean = false)
class SpeechLabViewModel(val runner: SpeechDiagnosticRunner) : ViewModel() {
    private val mutable = MutableStateFlow(SpeechLabState())
    val state = mutable.asStateFlow()
    private var session: SpeechDiagnosticSession? = null
    private var job: Job? = null
    private var finish: CompletableDeferred<Unit>? = null
    init { viewModelScope.launch(Dispatchers.IO) { mutable.value = mutable.value.copy(targets = runner.availableTargets()) } }
    fun capture(reference: String) = start {
        session?.close(); session = null
        finish = CompletableDeferred()
        mutable.value = mutable.value.copy(recording = true, hasSample = false,
            message = if (runner.language() == "lv") "Saki: Halo runas tests" else "Say: Halo speech test")
        session = runner.captureSession(finish, reference)
        mutable.value = mutable.value.copy(hasSample = true, message = session!!.summary(true))
    }
    fun finishCapture() { finish?.complete(Unit) }
    fun benchmark(targets: List<SpeechDiagnosticTarget>, repetitions: Int, reference: String) = start {
        val sample = (session ?: error("Capture a sample first")).copy(reference = reference)
        session = sample
        sample.results.clear()
        runner.benchmark(sample, targets, repetitions)
        mutable.value = mutable.value.copy(message = sample.summary(true))
    }
    private fun start(block: suspend () -> Unit) {
        if (job?.isCompleted == false) return
        job = viewModelScope.launch(Dispatchers.IO) {
            mutable.value = mutable.value.copy(busy = true)
            try { block() }
            catch (cancelled: CancellationException) { mutable.value = mutable.value.copy(message = "Cancelled; native cleanup finished"); throw cancelled }
            catch (error: Exception) { mutable.value = mutable.value.copy(message = error.message.orEmpty()) }
            finally { mutable.value = mutable.value.copy(busy = false, recording = false); finish = null }
        }
    }
    fun summary(includeTranscripts: Boolean) = session?.summary(includeTranscripts).orEmpty()
    fun cancel() {
        val active = job ?: return
        if (!active.isCompleted) {
            mutable.value = mutable.value.copy(message = "Cancellation requested; waiting for native decode and cleanup")
            active.cancel()
        }
    }
    fun dispose() {
        val active = job
        active?.cancel()
        if (active == null || active.isCompleted) clearSession()
        else active.invokeOnCompletion { clearSession() }
    }
    private fun clearSession() {
        session?.close(); session = null
        mutable.value = mutable.value.copy(busy = false, recording = false, hasSample = false, message = "")
    }
    override fun onCleared() { dispose() }
}
