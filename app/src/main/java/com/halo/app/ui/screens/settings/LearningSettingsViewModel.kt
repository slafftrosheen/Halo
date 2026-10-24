package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.halo.runtime.cognition.memory.LearningPreference
import com.halo.runtime.cognition.memory.LearningPreferences
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LearningSettingsViewModel(private val storage: Storage, initial: LearningPreferences,
    val memoryUse: StateFlow<Boolean>, private val setMemoryUse: (Boolean) -> Unit,
    private val owner: com.halo.app.learning.LearningConfigurationOwner = com.halo.app.learning.LearningConfigurationOwner(storage, initial)) : ViewModel() {
    val preferences = owner.preferences
    init { owner.snapshot() }
    fun mode(mode: LearningPreference) = owner.mode(mode)
    fun useMemory(value: Boolean) = setMemoryUse(value)
    fun option(key: String, value: Boolean) {
        owner.option(key, value)
    }
}
