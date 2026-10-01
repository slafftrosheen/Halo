package com.halo.app.data.settings

import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.halo.app.security.SecretMigrationV2
import com.halo.app.security.SecretStore

/** Repairs the old migrate-all bug without overwriting settings changed since migration. */
internal class MisplacedSettingsRecovery(
    private val legacy: SharedPreferences,
    private val secrets: SecretStore,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) =
        currentData.asMap().keys.any { it.name !in HALO_DATASTORE_KEYS }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val recovered = currentData.toMutablePreferences()
        val editor = legacy.edit()
        for ((key, value) in currentData.asMap()) {
            if (key.name in HALO_DATASTORE_KEYS) continue
            if (key.name in SecretMigrationV2.SECRET_KEYS) {
                require(value is String) { "Invalid migrated credential type" }
                val retained = secrets.migrateIfAbsent(key.name, legacy.getString(key.name, null)?.takeIf { it.isNotBlank() } ?: value)
                check(secrets.get(key.name) == retained) { "Credential recovery failed" }
            } else if (!legacy.contains(key.name)) {
                when (value) {
                    is String -> editor.putString(key.name, value)
                    is Boolean -> editor.putBoolean(key.name, value)
                    is Int -> editor.putInt(key.name, value)
                    is Long -> editor.putLong(key.name, value)
                    is Float -> editor.putFloat(key.name, value)
                    is Set<*> -> {
                        require(value.all { it is String })
                        editor.putStringSet(key.name, value.filterIsInstance<String>().toSet())
                    }
                    else -> error("Unsupported migrated setting type")
                }
            }
            recovered.remove(key)
        }
        check(editor.commit()) { "Could not recover migrated settings" }
        return recovered
    }

    override suspend fun cleanUp() = Unit
}
