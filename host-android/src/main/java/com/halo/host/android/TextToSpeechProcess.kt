package com.halo.host.android

import android.content.Context
import android.speech.tts.TextToSpeech
import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.ontology.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class TextToSpeechProcess(
    private val context: Context,
    private val executionBus: ExecutionBus
) : Process, TextToSpeech.OnInitListener {
    
    override val id: String = "driver.android.tts"
    override val name: String = "Android Text-To-Speech"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(context, this@TextToSpeechProcess)
        }
        
        launch {
            executionBus.subscribe(id).filterIsInstance<Outcome>().collect { outcome ->
                if (isInitialized && _state.value == ProcessState.Running) {
                    val message = outcome.resultPayload?.toString() ?: ""
                    if (message.isNotBlank()) {
                        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, outcome.id.value.toString())
                    }
                }
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _state.value = ProcessState.Crashed
            } else {
                isInitialized = true
            }
        } else {
            _state.value = ProcessState.Crashed
        }
    }
    
    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        isInitialized = false
        withContext(Dispatchers.Main) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
