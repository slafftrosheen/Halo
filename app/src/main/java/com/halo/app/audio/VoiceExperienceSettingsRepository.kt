package com.halo.app.audio

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InterruptionSensitivity(val thresholdMs: Long) { QUICK(250), NATURAL(450), DELIBERATE(700) }
data class VoiceExperienceSettings(
    val spokenInterruptionEnabled: Boolean = true,
    val interruptionSensitivity: InterruptionSensitivity = InterruptionSensitivity.NATURAL,
    val semanticInterruptionEnabled: Boolean = false,
    val streamingSpeechEnabled: Boolean = true,
    val responseStyle: SpokenResponseStyle = SpokenResponseStyle.NATURAL,
    val wearablePhoneFallback: Boolean = false,
    val useConnectedAudioAccessories: Boolean = true,
    val wearNearbyInteraction: Boolean = true,
) {
    fun responsePolicy() = VoiceResponsePolicy(streamingSpeechEnabled, responseStyle, wearablePhoneFallback)
    fun interruptionPolicy() = BargeInPolicyConfig(interruptionSensitivity.thresholdMs,
        semanticInterruptionEnabled, spokenInterruptionEnabled)
}
class VoiceExperienceSettingsRepository(private val storage: Storage) {
    private val lock = Any()
    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == storage.getString(key) } ?: default
    private fun read() = VoiceExperienceSettings(
        storage.getBoolean("voice_spoken_interruption", true),
        enumValue("voice_interruption_timing", InterruptionSensitivity.NATURAL),
        storage.getBoolean("voice_semantic_interruption", false),
        storage.getBoolean("voice_streaming_speech", true),
        enumValue("voice_response_style", SpokenResponseStyle.NATURAL),
        storage.getBoolean("wearable_phone_fallback", false),
        storage.getBoolean("use_connected_audio_accessories", true),
        storage.getBoolean("wear_nearby_interaction", true),
    )
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun readPersisted(): VoiceExperienceSettings = synchronized(lock) { read().also { mutable.value = it } }
    fun updateExpected(expected: VoiceExperienceSettings, value: VoiceExperienceSettings) = synchronized(lock) {
        check(read() == expected) { "Voice settings changed" }
        update(value)
    }
    fun update(value: VoiceExperienceSettings) = synchronized(lock) {
        storage.putBoolean("voice_spoken_interruption", value.spokenInterruptionEnabled)
        storage.putString("voice_interruption_timing", value.interruptionSensitivity.name)
        storage.putBoolean("voice_semantic_interruption", value.semanticInterruptionEnabled)
        storage.putBoolean("voice_streaming_speech", value.streamingSpeechEnabled)
        storage.putString("voice_response_style", value.responseStyle.name)
        storage.putBoolean("wearable_phone_fallback", value.wearablePhoneFallback)
        storage.putBoolean("use_connected_audio_accessories", value.useConnectedAudioAccessories)
        storage.putBoolean("wear_nearby_interaction", value.wearNearbyInteraction)
        mutable.value = value
    }
}
