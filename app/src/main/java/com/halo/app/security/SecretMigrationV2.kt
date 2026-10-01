package com.halo.app.security

import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V2 secret migration that expands the keystore-protected credential set to all
 * sensitive provider API keys and OAuth/companion secrets.
 *
 * Migration is:
 *  - idempotent
 *  - crash-safe (verification before plaintext removal)
 *  - backward-aware (legacy keys are migrated, then removed)
 *
 * Credential values are never logged.
 */
class SecretMigrationV2(
    private val storage: Storage,
    private val secretStore: SecretStore,
) {
    private val migrationVersionKey = "secret_migration_v2_version"
    private val currentVersion = 3

    suspend fun migrate() = withContext(Dispatchers.IO) {
        if (migrationVersion(storage) >= currentVersion) return@withContext

        val legacyKeys = SECRET_KEYS
        val migrated = mutableListOf<String>()
        try {
            for (key in legacyKeys) {
                val existing = storage.getString(key, null)
                if (!existing.isNullOrBlank()) {
                    val protected = secretStore.migrateIfAbsent(key, existing)
                    val restored = secretStore.get(key)
                    if (restored != protected) {
                        throw IllegalStateException("Secret migration verification failed for $key")
                    }
                    migrated.add(key)
                }
            }
            clearMigratedPlaintext(storage, migrated)
            setMigrationVersion(storage, currentVersion)
        } catch (e: Throwable) {
            // Crash-safe: if verification failed, do not remove plaintext yet.
            // A later retry can re-attempt after the underlying issue is fixed.
            throw e
        }
    }

    companion object {
        const val ELEVENLABS_KEY = "elevenlabs_api_key"
        const val OPENAI_KEY = "openai_api_key"
        const val ANTHROPIC_KEY = "anthropic_api_key"
        const val GEMINI_KEY = "gemini_api_key"
        const val GROQ_KEY = "groq_api_key"
        const val CEREBRAS_KEY = "cerebras_api_key"
        const val XAI_KEY = "xai_api_key"
        const val OPENCODE_ZEN_KEY = "opencode_zen_api_key"
        const val OPENCODE_COMPANION_PASSWORD_KEY = "opencode_companion_password"
        const val OPENCODE_COMPANION_CREDENTIAL_KEY = "opencode_companion_credential"
        const val CUSTOM_OPENAI_COMPATIBLE_CREDENTIAL_KEY = "custom_openai_compatible_credential"

        val SECRET_KEYS = setOf(
            SecretMigration.OPENROUTER_KEY,
            ELEVENLABS_KEY,
            OPENAI_KEY,
            ANTHROPIC_KEY,
            GEMINI_KEY,
            GROQ_KEY,
            CEREBRAS_KEY,
            XAI_KEY,
            OPENCODE_ZEN_KEY,
            OPENCODE_COMPANION_PASSWORD_KEY,
            OPENCODE_COMPANION_CREDENTIAL_KEY,
            CUSTOM_OPENAI_COMPATIBLE_CREDENTIAL_KEY,
        )

        private const val MIGRATION_VERSION_KEY = "secret_migration_v2_version"
        private const val CURRENT_VERSION = 3

        private fun migrationVersion(storage: Storage): Int =
            storage.getInt(MIGRATION_VERSION_KEY, 0)

        private fun setMigrationVersion(storage: Storage, version: Int) {
            storage.putInt(MIGRATION_VERSION_KEY, version)
        }

        private fun clearMigratedPlaintext(storage: Storage, keys: List<String>) {
            for (key in keys) {
                storage.remove(key)
            }
        }
    }
}
