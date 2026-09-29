package com.halo.app.backup

import com.halo.app.security.SecretMigrationV2
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guard that audits [BrainSnapshot] export and import paths so secrets never
 * leave the device in a backup and restored remote providers are returned as
 * NEEDS_AUTH when credentials are absent.
 *
 * Export may contain provider selection, endpoint, model selection, role
 * binding, auth METHOD and connection display metadata. It must NOT contain API
 * keys, bearer tokens, OAuth access/refresh tokens, or companion passwords.
 *
 * Import must return provider as NEEDS_AUTH after restore where credentials are
 * absent.
 */
object BrainSnapshotSecretsGuard {

    /** Keys that are never exported in a Brain snapshot. */
    val protectedKeys: Set<String> = SecretMigrationV2.SECRET_KEYS

    /**
     * Strip any secret-bearing keys from settings before export.
     *
     * This is intentionally idempotent and content-preserving for non-secret
     * settings.
     */
    suspend fun sanitizeSettingsForExport(
        settings: Map<String, String>,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        settings.filterKeys { key -> key !in protectedKeys }
    }

    /**
     * After import, providers whose required credential is missing are returned
     * as needing authentication. Callers should restore selection/endpoint/model
     * without restoring secret values.
     */
    suspend fun providersNeedingAuth(
        storage: Storage,
        providerKeys: Set<String>,
    ): Set<String> = withContext(Dispatchers.IO) {
        providerKeys.filter { key ->
            val value = storage.getString(key, null)
            value.isNullOrBlank()
        }.toSet()
    }

    /**
     * True when a key holds a non-empty secret value in the current storage
     * backend.
     */
    suspend fun hasSecret(key: String, storage: Storage): Boolean = withContext(Dispatchers.IO) {
        !storage.getString(key, null).isNullOrBlank()
    }
}
