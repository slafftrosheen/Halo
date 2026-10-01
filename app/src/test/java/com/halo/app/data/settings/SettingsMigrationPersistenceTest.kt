package com.halo.app.data.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.halo.app.security.SecretStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsMigrationPersistenceTest {
    private class Secrets : SecretStore {
        val values = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override suspend fun get(key: String) = values[key]
        override suspend fun remove(key: String) { values.remove(key) }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun scopedMigrationLeavesOtherSettingsAndCredentialsInTheirOwner() = runBlocking {
        val name = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("speech_locale", "lv").putString("speech_tts_provider", "provider.elevenlabs.tts")
            .putString("elevenlabs_voice_id", "voice-123").putString("elevenlabs_api_key", "test-key")
            .putBoolean("wearable_phone_fallback", true).commit()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
                migrations = listOf(SharedPreferencesMigration(context, name, keysToMigrate = HALO_DATASTORE_KEYS)),
                produceFile = { File(context.cacheDir, "$name.preferences_pb") })
            assertEquals("lv", store.data.first()[stringPreferencesKey("speech_locale")])
            assertFalse(prefs.contains("speech_locale"))
            assertEquals("provider.elevenlabs.tts", prefs.getString("speech_tts_provider", null))
            assertEquals("voice-123", prefs.getString("elevenlabs_voice_id", null))
            assertEquals("test-key", prefs.getString("elevenlabs_api_key", null))
            assertTrue(prefs.getBoolean("wearable_phone_fallback", false))
        } finally { job.cancelAndJoin() }
    }

    @Test fun historicalMigrationRecoversChoicesAndProtectsSecretsAcrossReopen() = runBlocking {
        val name = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "$name.preferences_pb")
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val secrets = Secrets()
        prefs.edit().putString("ai_last_model", "newer-model").commit()
        var job = SupervisorJob()
        var store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        store.edit {
            it[stringPreferencesKey("speech_locale")] = "lv"
            it[stringPreferencesKey("speech_tts_provider")] = "provider.elevenlabs.tts"
            it[stringPreferencesKey("elevenlabs_voice_id")] = "voice-123"
            it[stringPreferencesKey("elevenlabs_api_key")] = "test-key"
            it[stringPreferencesKey("ai_last_model")] = "older-model"
            it[booleanPreferencesKey("wearable_phone_fallback")] = true
            it[intPreferencesKey("custom_count")] = 7
        }
        job.cancelAndJoin()
        job = SupervisorJob()
        try {
            store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
                migrations = listOf(MisplacedSettingsRecovery(prefs, secrets)), produceFile = { file })
            val recovered = store.data.first()
            assertEquals(setOf("speech_locale"), recovered.asMap().keys.map { it.name }.toSet())
            assertEquals("voice-123", prefs.getString("elevenlabs_voice_id", null))
            assertEquals("provider.elevenlabs.tts", prefs.getString("speech_tts_provider", null))
            assertEquals("newer-model", prefs.getString("ai_last_model", null))
            assertTrue(prefs.getBoolean("wearable_phone_fallback", false))
            assertEquals(7, prefs.getInt("custom_count", 0))
            assertFalse(prefs.contains("elevenlabs_api_key"))
            assertEquals("test-key", secrets.get("elevenlabs_api_key"))
        } finally { job.cancelAndJoin() }
        job = SupervisorJob()
        try {
            store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            assertEquals(setOf("speech_locale"), store.data.first().asMap().keys.map { it.name }.toSet())
            assertEquals("voice-123", context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("elevenlabs_voice_id", null))
        } finally { job.cancelAndJoin() }
    }
    @Test fun recoveryPreservesNewerProtectedCredentialAndIsIdempotent() = runBlocking {
        val prefs = context.getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE)
        val secrets = Secrets()
        secrets.put("elevenlabs_api_key", "new-key")
        val old = androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("elevenlabs_api_key") to "old-key",
            stringPreferencesKey("elevenlabs_voice_id") to "voice-123",
        )
        val recovery = MisplacedSettingsRecovery(prefs, secrets)
        val migrated = recovery.migrate(old)
        assertEquals("new-key", secrets.get("elevenlabs_api_key"))
        assertFalse(prefs.contains("elevenlabs_api_key"))
        assertFalse(recovery.shouldMigrate(migrated))
        assertEquals("voice-123", prefs.getString("elevenlabs_voice_id", null))
    }

    @Test fun failedCredentialRecoveryDoesNotRemoveOriginalSettings() = runBlocking {
        val prefs = context.getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE)
        val failedSecrets = object : SecretStore {
            override suspend fun put(key: String, value: String?) { error("Disk unavailable") }
            override suspend fun get(key: String): String? = null
            override suspend fun remove(key: String) = Unit
        }
        val old = androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("elevenlabs_voice_id") to "voice-123",
            stringPreferencesKey("elevenlabs_api_key") to "test-key",
        )
        try { MisplacedSettingsRecovery(prefs, failedSecrets).migrate(old); fail("Expected persistence failure") }
        catch (_: IllegalStateException) { }
        assertEquals("test-key", old[stringPreferencesKey("elevenlabs_api_key")])
        assertFalse(prefs.contains("elevenlabs_voice_id"))
        assertFalse(prefs.contains("elevenlabs_api_key"))
    }

}
