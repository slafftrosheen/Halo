package com.halo.app.ui.developer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.Memory
import com.halo.app.data.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    val allMemories: StateFlow<List<Memory>> = repository.allMemories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addMemory(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.insert(Memory(content = content.trim()))
        }
    }

    fun updateMemory(memory: Memory, newContent: String) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            repository.update(memory.copy(content = newContent.trim()))
        }
    }

    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            repository.delete(memory)
        }
    }
}

class MemoryViewModelFactory(private val repository: MemoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MemoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
