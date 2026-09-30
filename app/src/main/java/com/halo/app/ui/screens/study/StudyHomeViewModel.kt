package com.halo.app.ui.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.study.StudyUseCases
import com.halo.app.study.StudyStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudyHomeViewModel(private val study: StudyUseCases) : ViewModel() {
    val sessions = study.observeActiveSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val due = study.observeDuePracticeItems(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val notice = message.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    fun start(topic: String, language: String) {
        if (working.value || topic.isBlank()) return
        working.value = true
        viewModelScope.launch {
            try {
                when (val result = study.startTopic(topic, language.trim().takeIf { it.isNotBlank() })) {
                    is StudyStartResult.Started -> events.send(result.session.id)
                    is StudyStartResult.Unavailable -> message.value = result.reason.explanation
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Couldn't start yet. Your topic is still here." }
            finally { working.value = false }
        }
    }
    class Factory(private val study: StudyUseCases) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudyHomeViewModel(study) as T
    }
}
