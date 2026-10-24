package com.halo.app.di.tools.host

import com.halo.app.audio.VoiceExperienceSettingsRepository
import com.halo.app.ai.SpeechSynthesisRegistry
import com.halo.app.ui.screens.settings.SpeechLanguageSettingsRepository
import com.halo.app.ui.screens.settings.VoiceSettingsViewModel
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared settings calls only. Readability/availability are never promoted into physical voice acceptance. */
internal class LiveVoiceConfigurationAccess(private val storage: Storage, private val model: VoiceSettingsViewModel,
    private val language: SpeechLanguageSettingsRepository, private val experience: VoiceExperienceSettingsRepository,
    private val extras: VoiceConfigurationExtras, private val sourceRevision: () -> Long) {
    suspend fun read(): VoiceConfigurationState {
        language.loadAndMigrate()
        val ui = model.uiState.value
        return VoiceConfigurationState(language.languageMode.value, experience.readPersisted(), model.cloudSynthesisFallback(),
            ui.availableLocales.map { it.tag }.distinct().take(32), extras.read(), ui.isTesting || ui.isDownloading, sourceRevision(),
            recognitionProvider = ui.selectedProviderId,
            recognitionChoices = listOf("AUTOMATIC") + com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels
                .filter { ui.models[it.id] == true && it.supportsLanguage(language.languageMode.value) }.map { it.providerId },
            synthesisProvider = model.synthesisRegistry?.selector?.ttsSelection?.value?.id,
            synthesisChoices = model.synthesisRegistry?.let { listOf("AUTOMATIC") + it.providers.map { provider -> provider.metadata.id } }.orEmpty())
    }
    suspend fun apply(before: VoiceConfigurationState, change: VoiceConfigurationChange) {
        check(read() == before) { "Voice settings changed" }
        val old = before.experience
        val next = old.copy(spokenInterruptionEnabled = change.spokenInterruptionEnabled ?: old.spokenInterruptionEnabled,
            interruptionSensitivity = change.interruptionSensitivity ?: old.interruptionSensitivity,
            semanticInterruptionEnabled = change.semanticInterruptionEnabled ?: old.semanticInterruptionEnabled,
            streamingSpeechEnabled = change.streamingSpeechEnabled ?: old.streamingSpeechEnabled,
            responseStyle = change.responseStyle ?: old.responseStyle,
            wearablePhoneFallback = change.wearablePhoneFallback ?: old.wearablePhoneFallback,
            useConnectedAudioAccessories = change.useConnectedAudioAccessories ?: old.useConnectedAudioAccessories,
            wearNearbyInteraction = change.wearNearbyInteraction ?: old.wearNearbyInteraction)
        withContext(Dispatchers.IO) {
            if (next != old) experience.updateExpected(old, next)
            change.synthesisFallback?.let {
                check(storage.getBoolean(SpeechSynthesisRegistry.FALLBACK, true) == before.synthesisFallback) { "Synthesis fallback changed" }
                model.setCloudSynthesisFallbackExpected(it, before.synthesisFallback)
                check(storage.getBoolean(SpeechSynthesisRegistry.FALLBACK, !it) == it) { "Synthesis fallback was not saved" }
            }
        }
        change.locale?.let { model.selectLocaleAndAwait(it, before.locale) }
        extras.apply(before.additional, change)
        model.refreshConfiguredVoicePreferences()
    }
}
