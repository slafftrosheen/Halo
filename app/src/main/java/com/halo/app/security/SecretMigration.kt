package com.halo.app.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SecretMigration(
    private val context: Context,
    private val secretStore: SecretStore,
) {
    private val legacy = context.getSharedPreferences("halo_storage", Context.MODE_PRIVATE)

    suspend fun migrate() = withContext(Dispatchers.IO) {
        if (legacy.getInt(MIGRATION_VERSION_KEY, 0) >= CURRENT_VERSION && !legacy.contains(OPENROUTER_KEY)) return@withContext
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val migratedPreferences = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { context.preferencesDataStoreFile("halo_settings") },
        )
        val preferenceKey = stringPreferencesKey(OPENROUTER_KEY)
        val existing = legacy.getString(OPENROUTER_KEY, null)
            ?: migratedPreferences.data.first()[preferenceKey]
        if (!existing.isNullOrBlank()) {
            val protected = secretStore.migrateIfAbsent(OPENROUTER_KEY, existing)
            check(secretStore.get(OPENROUTER_KEY) == protected) { "Secret migration verification failed" }
        }
        migratedPreferences.edit { it.remove(preferenceKey) }
        dataStoreScope.cancel()
        check(legacy.edit()
            .remove(OPENROUTER_KEY)
            .putInt(MIGRATION_VERSION_KEY, CURRENT_VERSION)
            .commit()) { "Could not finish secret migration" }
        check(!legacy.contains(OPENROUTER_KEY)) { "Legacy credential remains after migration" }
    }

    companion object {
        const val OPENROUTER_KEY = "openrouter_api_key"
        private const val MIGRATION_VERSION_KEY = "secret_migration_version"
        private const val CURRENT_VERSION = 1
    }
}
