package com.halo.app.ai

import com.halo.providers.openrouter.OpenRouterRecognitionProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One configuration owner for the Listening screen and typed/voice assistant operations. */
class OpenRouterRecognitionControl(val provider: OpenRouterRecognitionProvider, val modes: PrivacyModeStore,
    private val canConfigure: () -> Boolean, private val save: (PrivacyMode, ModeProfile, Long) -> Unit) {
    private val operations = Mutex()
    suspend fun refresh() = operations.withLock { requireReady(); provider.refreshModels() }
    suspend fun configure(model: String, revision: Long, sourceVersion: String) = operations.withLock {
        requireReady()
        check(modes.revision == revision && provider.state.value.sourceVersion == sourceVersion) { "Listening settings changed. Read them again." }
        provider.validateSelection(model)
        val profile = modes.profile(PrivacyMode.CLOUD).let { it.copy(strings = it.strings + mapOf(
            OpenRouterRecognitionProvider.MODEL to model, "speech_stt_provider" to OpenRouterRecognitionProvider.ID)) }
        save(PrivacyMode.CLOUD, profile, revision)
        check(modes.profile(PrivacyMode.CLOUD) == profile) { "Listening settings readback differs" }
        provider.reloadConfigurationNow()
    }
    private fun requireReady() {
        check(canConfigure() && modes.mode.value == PrivacyMode.CLOUD && modes.sharingAcknowledged) { "Open Power mode and Listening settings first" }
    }
}
