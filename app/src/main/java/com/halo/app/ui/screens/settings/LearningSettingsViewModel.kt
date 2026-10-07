package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.halo.runtime.cognition.memory.LearningPreference
import com.halo.runtime.cognition.memory.LearningPreferences
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LearningSettingsViewModel(private val storage: Storage, initial: LearningPreferences,
    val memoryUse: StateFlow<Boolean>, private val setMemoryUse: (Boolean) -> Unit) : ViewModel() {
    private val mutable = MutableStateFlow(initial)
    val preferences = mutable.asStateFlow()
    fun mode(mode: LearningPreference) { storage.putString("learning_mode", mode.name); mutable.value = mutable.value.copy(mode = mode) }
    fun useMemory(value: Boolean) = setMemoryUse(value)
    fun option(key: String, value: Boolean) {
        if (mutable.value.mode != LearningPreference.SUGGEST) return
        val updated = when (key) {
            "learning_memories" -> mutable.value.copy(suggestMemories = value)
            "learning_procedures" -> mutable.value.copy(suggestProcedures = value)
            "learning_automations" -> mutable.value.copy(suggestAutomations = value)
            "learning_model_reflection" -> mutable.value.copy(modelReflection = value)
            else -> return
        }
        storage.putBoolean(key, value); mutable.value = updated
    }
}
