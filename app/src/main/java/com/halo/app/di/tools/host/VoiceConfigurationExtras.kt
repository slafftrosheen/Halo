package com.halo.app.di.tools.host

import com.halo.app.audio.KokoroSpeechProvider
import com.halo.app.controls.ScopedSpeechMediaControls
import com.halo.app.ui.components.HaloReadAloudController
import com.halo.sdk.host.AccessoryAction
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Optional canonical owner callbacks. Availability is explicit; no replacement persistence or audio engine. */
class VoiceConfigurationExtras(
    val read: suspend () -> VoiceAdditionalState = { VoiceAdditionalState() },
    val apply: suspend (VoiceAdditionalState, VoiceConfigurationChange) -> Unit = { _, change ->
        check(change.kokoroVoiceId == null && change.scopedControlAction == null && change.resetCustomControls == null &&
            change.followWords == null && change.autoReadAnswers == null && change.readingSpeed == null && change.wearNearbyInteraction == null)
    },
) {
    companion object {
        fun fromOwners(storage: Storage, reading: HaloReadAloudController, scoped: ScopedSpeechMediaControls,
            controlAction: () -> AccessoryAction, controlRevision: () -> Long, resetCustomControls: () -> Unit,
            kokoro: KokoroSpeechProvider?, wearNearbyChanged: (Boolean) -> Unit): VoiceConfigurationExtras =
            VoiceConfigurationExtras(read = {
                val version = kokoro?.state?.value?.modelVersion
                val count = if (version != null && kokoro?.canUseForSpeech("en") == true) if (kokoro.multilingualInstalled()) 103 else 11 else 0
                VoiceAdditionalState(if (count > 0) kokoro?.selectedVoice() else null, version, count,
                    controlAction().name, controlRevision(), reading.followWords.value, reading.autoReadAnswers.value,
                    reading.speedPreference.value, true)
            }, apply = { before, change ->
                withContext(Dispatchers.IO) {
                    change.kokoroVoiceId?.let { requireNotNull(kokoro).selectVoice(it, requireNotNull(before.kokoroVersion), before.kokoroVoiceId) }
                    change.scopedControlAction?.let { action ->
                        check(controlRevision() == before.controlRevision) { "Audio control mapping changed" }
                        scoped.configure(AccessoryAction.valueOf(action))
                        check(controlAction().name == action) { "Audio control mapping was not saved" }
                    }
                    if (change.resetCustomControls == true) {
                        check(controlRevision() == before.controlRevision) { "Audio control mapping changed" }
                        scoped.configure(AccessoryAction.SystemDefault); resetCustomControls()
                        check(controlAction() == AccessoryAction.SystemDefault) { "Audio controls were not reset" }
                    }
                    change.followWords?.let { reading.setFollowWordsExpected(it, requireNotNull(before.followWords)); check(reading.followWords.value == it) }
                    change.autoReadAnswers?.let { reading.setAutoReadAnswersExpected(it, requireNotNull(before.autoReadAnswers)); check(reading.autoReadAnswers.value == it) }
                    change.readingSpeed?.let { reading.setSpeedPreferencePersisted(it, before.readingSpeed); check(reading.speedPreference.value == it) }
                    change.wearNearbyInteraction?.let(wearNearbyChanged)
                    // Readback checks owner state and the actual persistent values; no playback is started.
                    change.followWords?.let { check(storage.getBoolean("read_aloud_follow_words", !it) == it) }
                    change.autoReadAnswers?.let { check(storage.getBoolean("read_aloud_auto_answers", !it) == it) }
                    change.readingSpeed?.let { check(storage.getString("read_aloud_speed")?.toDoubleOrNull() == it) }
                }
            })
    }
}
