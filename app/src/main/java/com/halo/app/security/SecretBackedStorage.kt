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

    override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
        strings.filterKeys { it in SECRET_KEYS }.forEach { (key, value) -> secrets.putBlocking(key, value) }
        require(booleans.keys.none { it in SECRET_KEYS })
        delegate.putBatch(strings.filterKeys { it !in SECRET_KEYS }, booleans)
    }

    override fun remove(key: String) {
        if (key in SECRET_KEYS) secrets.putBlocking(key, null) else delegate.remove(key)
    }

    override fun clear() {
        delegate.clear()
        SECRET_KEYS.forEach { secrets.putBlocking(it, null) }
    }

    companion object {
        private val SECRET_KEYS = SecretMigrationV2.SECRET_KEYS
    }
}
