package com.halo.app.ai

import com.halo.providers.openrouter.OpenRouterSpeechProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The screen and assistant save one exact Power profile through its existing owner. */
class OpenRouterSpeechControl(
    val provider: OpenRouterSpeechProvider,
    val modes: PrivacyModeStore,
    private val canConfigure: () -> Boolean,
    private val save: (PrivacyMode, ModeProfile, Long) -> Unit,
) {
    private val operations = Mutex()
    suspend fun refresh() = operations.withLock {
        requireReady()
        provider.refreshModels()
    }
    suspend fun configure(model: String, voice: String, revision: Long, sourceVersion: String) = operations.withLock {
        requireReady()
        check(modes.revision == revision && provider.state.value.sourceVersion == sourceVersion) { "Voice settings changed. Read them again." }
        provider.validateSelection(model, voice)
        val profile = modes.profile(PrivacyMode.CLOUD).let { it.copy(strings = it.strings + mapOf(
            OpenRouterSpeechProvider.MODEL to model, OpenRouterSpeechProvider.VOICE to voice.trim(),
            "speech_tts_provider" to OpenRouterSpeechProvider.ID)) }
        save(PrivacyMode.CLOUD, profile, revision)
        check(modes.profile(PrivacyMode.CLOUD) == profile) { "Voice settings readback differs" }
        provider.reloadConfigurationNow()
    }
    private fun requireReady() {
        check(canConfigure() && modes.mode.value == PrivacyMode.CLOUD && modes.sharingAcknowledged) { "Open Power mode and voice settings first" }
    }
}
