package com.halo.app.security

import com.halo.sdk.runtime.Storage

/** Keeps the existing provider contract while routing credential keys out of preferences. */
class SecretBackedStorage(
    private val delegate: Storage,
    private val secrets: AndroidKeystoreSecretStore,
) : Storage by delegate {
    override fun putString(key: String, value: String?) {
        if (key in SECRET_KEYS) secrets.putBlocking(key, value) else delegate.putString(key, value)
    }

    override fun getString(key: String, defaultValue: String?): String? =
        if (key in SECRET_KEYS) secrets.getBlocking(key) ?: defaultValue else delegate.getString(key, defaultValue)

    override fun remove(key: String) {
        if (key in SECRET_KEYS) secrets.putBlocking(key, null) else delegate.remove(key)
    }

    override fun clear() {
        delegate.clear()
        SECRET_KEYS.forEach { secrets.putBlocking(it, null) }
    }

    companion object {
        private val SECRET_KEYS = setOf(
            SecretMigration.OPENROUTER_KEY,
            com.halo.app.security.SecretMigrationV2.OPENAI_KEY,
            com.halo.app.security.SecretMigrationV2.ANTHROPIC_KEY,
            com.halo.app.security.SecretMigrationV2.GEMINI_KEY,
            com.halo.app.security.SecretMigrationV2.GROQ_KEY,
            com.halo.app.security.SecretMigrationV2.CEREBRAS_KEY,
            com.halo.app.security.SecretMigrationV2.XAI_KEY,
            com.halo.app.security.SecretMigrationV2.OPENCODE_ZEN_KEY,
            com.halo.app.security.SecretMigrationV2.OPENCODE_COMPANION_PASSWORD_KEY,
            com.halo.app.security.SecretMigrationV2.OPENCODE_COMPANION_CREDENTIAL_KEY,
            com.halo.app.security.SecretMigrationV2.CUSTOM_OPENAI_COMPATIBLE_CREDENTIAL_KEY,
        )
    }
}
