package com.halo.app.security

import com.halo.sdk.provider.ProviderSecretStore

/**
 * App secret persistence contract (Android keystore-backed implementations).
 *
 * Extends the provider-layer [ProviderSecretStore] so provider modules can
 * persist OAuth/companion secrets without depending on the app module; app
 * objects implementing [SecretStore] satisfy both contracts.
 */
interface SecretStore : ProviderSecretStore {
    override suspend fun put(key: String, value: String?)
    override suspend fun get(key: String): String?
    /** Preserve an already protected credential when migrating older preferences. */
    suspend fun migrateIfAbsent(key: String, legacyValue: String): String {
        val existing = get(key)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        put(key, legacyValue)
        return legacyValue
    }

    override suspend fun remove(key: String)
}
