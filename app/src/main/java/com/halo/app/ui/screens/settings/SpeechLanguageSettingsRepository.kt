package com.halo.app.ui.screens.settings

import com.halo.app.data.settings.HaloSettingsRepository
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class SpeechLanguageSettingsRepository(
    private val settingsRepository: HaloSettingsRepository
) {
    private val _languageMode = MutableStateFlow(Locale.getDefault().language.ifBlank { "en" })
    val languageMode: StateFlow<String> = _languageMode

    suspend fun loadAndMigrate() {
        val settings = settingsRepository.settings.first()
        val legacy = settings.speechLanguageMode?.let(::normalize)?.ifBlank { null }
        val userSelected = settings.speechLanguageUserSelected
        val selected = if ((legacy == null || legacy == "auto") && !userSelected) {
            Locale.getDefault().language.ifBlank { "en" }
        } else legacy ?: Locale.getDefault().language.ifBlank { "en" }
        settingsRepository.setSpeechLanguage(selected, userSelected, CURRENT_VERSION)
        _languageMode.value = selected
    }

    suspend fun select(tag: String) {
        val language = normalize(tag)
        require(language.isNotBlank()) { "Choose a speech language" }
        settingsRepository.setSpeechLanguage(language, true, CURRENT_VERSION)
        _languageMode.value = language
    }

    private fun normalize(tag: String) = tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')

    private companion object {
        const val CURRENT_VERSION = 1
    }
}
