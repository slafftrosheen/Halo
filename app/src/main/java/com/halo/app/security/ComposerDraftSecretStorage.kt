package com.halo.app.security

import com.halo.app.ui.screens.home.ComposerDraftStore
import com.halo.sdk.runtime.Storage

/** String-only adapter for the one encrypted composer snapshot. Never delegates to preferences. */
class ComposerDraftSecretStorage(private val secrets: AndroidKeystoreSecretStore) : Storage {
    private fun checkedKey(key: String): String { require(key == ComposerDraftStore.STORAGE_KEY); return key }
    override fun getString(key: String, defaultValue: String?): String? = secrets.getBlocking(checkedKey(key)) ?: defaultValue
    override fun putString(key: String, value: String?) = secrets.putBlocking(checkedKey(key), value)
    override fun remove(key: String) = secrets.putBlocking(checkedKey(key), null)
    override fun clear() = remove(ComposerDraftStore.STORAGE_KEY)
    override fun getInt(key: String, defaultValue: Int): Int = error("Composer snapshot is string-only")
    override fun putInt(key: String, value: Int): Unit = error("Composer snapshot is string-only")
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = error("Composer snapshot is string-only")
    override fun putBoolean(key: String, value: Boolean): Unit = error("Composer snapshot is string-only")
}
