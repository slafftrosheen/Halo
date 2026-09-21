package com.halo.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class HaloSettings(
    val speechLanguageMode: String? = null,
    val speechLanguageModeVersion: Int = 0,
    val speechLanguageUserSelected: Boolean = false,
    val backupTreeUri: String? = null,
    val automaticBackup: String = "OFF",
    val backupRetention: Int = 5,
    val restoreGeneration: Int = 0,
)

/** Typed, asynchronous owner for user preferences; legacy SharedPreferences migrate once. */
class HaloSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(appContext, LEGACY_STORE_NAME)),
        produceFile = { appContext.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    val settings: Flow<HaloSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            HaloSettings(
                speechLanguageMode = preferences[SPEECH_LANGUAGE],
                speechLanguageModeVersion = preferences[SPEECH_LANGUAGE_VERSION] ?: 0,
                speechLanguageUserSelected = preferences[SPEECH_LANGUAGE_USER_SELECTED] ?: false,
                backupTreeUri = preferences[BACKUP_TREE_URI],
                automaticBackup = preferences[AUTOMATIC_BACKUP] ?: "OFF",
                backupRetention = preferences[BACKUP_RETENTION] ?: 5,
                restoreGeneration = preferences[RESTORE_GENERATION] ?: 0,
            )
        }

    suspend fun setSpeechLanguage(mode: String, userSelected: Boolean, version: Int) {
        dataStore.edit { preferences ->
            preferences[SPEECH_LANGUAGE] = mode
            preferences[SPEECH_LANGUAGE_USER_SELECTED] = userSelected
            preferences[SPEECH_LANGUAGE_VERSION] = version
        }
    }

    suspend fun setBackupTreeUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(BACKUP_TREE_URI) else preferences[BACKUP_TREE_URI] = uri
        }
    }

    suspend fun setAutomaticBackup(value: String) { dataStore.edit { it[AUTOMATIC_BACKUP] = value } }
    suspend fun setBackupRetention(value: Int) { dataStore.edit { it[BACKUP_RETENTION] = value.coerceIn(3, 20) } }
    suspend fun setRestoreGeneration(value: Int) { dataStore.edit { it[RESTORE_GENERATION] = value } }

    private companion object {
        const val DATASTORE_NAME = "halo_settings"
        const val LEGACY_STORE_NAME = "halo_storage"
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_locale")
        val SPEECH_LANGUAGE_VERSION = intPreferencesKey("speech_language_mode_version")
        val SPEECH_LANGUAGE_USER_SELECTED = booleanPreferencesKey("speech_language_user_selected")
        val BACKUP_TREE_URI = stringPreferencesKey("backup_tree_uri")
        val AUTOMATIC_BACKUP = stringPreferencesKey("automatic_backup")
        val BACKUP_RETENTION = intPreferencesKey("backup_retention")
        val RESTORE_GENERATION = intPreferencesKey("restore_generation")
    }
}
