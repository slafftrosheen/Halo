package com.halo.app.ai

import com.halo.sdk.provider.SpeechProviderSelection
import com.halo.sdk.provider.SpeechProviderSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultSpeechProviderSelector(
    private val repository: SpeechSelectionRepository
) : SpeechProviderSelector {
    
    private val _sttSelection = MutableStateFlow(SpeechProviderSelection(repository.readSelection().sttProviderId))
    override val sttSelection: StateFlow<SpeechProviderSelection> = _sttSelection.asStateFlow()
    
    private val _ttsSelection = MutableStateFlow(SpeechProviderSelection(repository.readSelection().ttsProviderId))
    override val ttsSelection: StateFlow<SpeechProviderSelection> = _ttsSelection.asStateFlow()

    fun reloadSelection() {
        val saved = repository.readSelection()
        _sttSelection.value = SpeechProviderSelection(saved.sttProviderId)
        _ttsSelection.value = SpeechProviderSelection(saved.ttsProviderId)
    }

    override fun selectStt(id: String) {
        val current = repository.readSelection()
        val next = current.copy(sttProviderId = id)
        repository.writeSelection(next)
        _sttSelection.value = SpeechProviderSelection(id)
    }

    override fun selectTts(id: String) {
        val current = repository.readSelection()
        val next = current.copy(ttsProviderId = id)
        repository.writeSelection(next)
        _ttsSelection.value = SpeechProviderSelection(id)
    }
}
