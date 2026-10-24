package com.halo.app.learning

import com.halo.runtime.cognition.memory.LearningPreference
import com.halo.runtime.cognition.memory.LearningPreferences
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

data class LearningConfigurationSnapshot(val preferences: LearningPreferences, val sourceVersion: String)

/** Shared presentation/settings owner. Reflection and approval remain in the existing Runtime. */
class LearningConfigurationOwner(private val storage: Storage, initial: LearningPreferences) {
    private val lock = Any()
    private val generation = UUID.randomUUID().toString()
    private var revision = 0L
    private val mutable = MutableStateFlow(initial)
    val preferences = mutable.asStateFlow()
    private fun read() = LearningPreferences(
        LearningPreference.fromStored(storage.getString("learning_mode")),
        storage.getBoolean("learning_memories", true),
        storage.getBoolean("learning_procedures", true),
        storage.getBoolean("learning_automations", true),
        storage.getBoolean("learning_model_reflection", false))
    fun snapshot(): LearningConfigurationSnapshot = synchronized(lock) {
        val actual = read()
        if (actual != mutable.value) { revision++; mutable.value = actual }
        val digest = MessageDigest.getInstance("SHA-256").digest("$generation|$revision|$actual".toByteArray())
            .joinToString("") { "%02x".format(it) }
        LearningConfigurationSnapshot(actual, "learning:$digest")
    }
    fun apply(expected: String, next: LearningPreferences): LearningConfigurationSnapshot = synchronized(lock) {
        check(snapshot().sourceVersion == expected) { "Learning settings changed. Read them again." }
        if (next == mutable.value) return@synchronized snapshot()
        // Consume before writes: partial/uncertain storage failure must not reuse an old token.
        revision++
        try {
            storage.putBoolean("learning_memories", next.suggestMemories)
            storage.putBoolean("learning_procedures", next.suggestProcedures)
            storage.putBoolean("learning_automations", next.suggestAutomations)
            storage.putBoolean("learning_model_reflection", next.modelReflection)
            storage.putString("learning_mode", next.mode.name)
        } finally { mutable.value = read() }
        snapshot().also { check(it.preferences == next) { "Learning settings readback differs" } }
    }
    fun mode(value: LearningPreference) = synchronized(lock) { val old = snapshot(); apply(old.sourceVersion, old.preferences.copy(mode = value)); Unit }
    fun option(key: String, value: Boolean) = synchronized(lock) {
        val old = snapshot()
        if (old.preferences.mode != LearningPreference.SUGGEST) return@synchronized
        val next = when (key) {
            "learning_memories" -> old.preferences.copy(suggestMemories = value)
            "learning_procedures" -> old.preferences.copy(suggestProcedures = value)
            "learning_automations" -> old.preferences.copy(suggestAutomations = value)
            "learning_model_reflection" -> old.preferences.copy(modelReflection = value)
            else -> return@synchronized
        }
        apply(old.sourceVersion, next); Unit
    }
}
