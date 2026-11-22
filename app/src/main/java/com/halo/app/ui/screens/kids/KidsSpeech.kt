package com.halo.app.ui.screens.kids

import com.halo.app.audio.readingProvenance
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.provider.RemoteDataCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.halo.app.ui.components.VoiceFieldCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the app-wide voice mode after the previous microphone owner releases. */
internal suspend fun readKidsText(id: String, text: String, language: String? = null, storyId: String? = null,
    localOnly: Boolean = false): Boolean {
    if (text.isBlank()) return false
    VoiceFieldCancellation.stop()
    val ticket = VoiceFieldCancellation.epoch.value
    val released = withTimeoutOrNull(3000) { VoiceFieldCancellation.activeFields.first { it.isEmpty() }; true } == true
    if (!released || ticket != VoiceFieldCancellation.epoch.value) return false
    val category = if (storyId == null) RemoteDataCategory.TEXT else RemoteDataCategory.WORKSPACE
    val sources = if (storyId == null) emptySet() else setOf(storyId)
    val provenance = if (localOnly) null else withContext(Dispatchers.IO) { readingProvenance(category, sources) }
    if (ticket != VoiceFieldCancellation.epoch.value) return false
    val provider = if (localOnly) "provider.android.tts" else Graph.speechSynthesisRegistry.selector.ttsSelection.value.id
    Graph.readAloud.start(id, text, provider,
        cloudApproved = provenance != null, language = language, remoteProvenance = provenance,
        beforeSentence = { provenance == null || sources.isEmpty() || readingProvenance(category, sources) == provenance })
    return true
}
