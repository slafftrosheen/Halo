package com.halo.app.ui.screens.kids

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.components.VoiceFieldCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Child speech is always private and waits for the previous microphone owner to release. */
internal suspend fun readKidsText(id: String, text: String, language: String? = null): Boolean {
    if (text.isBlank()) return false
    VoiceFieldCancellation.stop()
    val ticket = VoiceFieldCancellation.epoch.value
    val released = withTimeoutOrNull(3000) { VoiceFieldCancellation.activeFields.first { it.isEmpty() }; true } == true
    if (!released || ticket != VoiceFieldCancellation.epoch.value) return false
    Graph.readAloud.start(id, text, Graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
        cloudApproved = false, language = language)
    return true
}
