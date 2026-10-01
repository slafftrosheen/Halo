package com.halo.host.android

import android.content.Context
import android.content.SharedPreferences
import com.halo.sdk.runtime.Storage

/**
 * Android implementation of [Storage] using [SharedPreferences].
 */
class AndroidStorage(private val context: Context, private val name: String = "halo_storage") : Storage {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
        val editor = prefs.edit()
        strings.forEach { (key, value) -> editor.putString(key, value) }
        booleans.forEach { (key, value) -> editor.putBoolean(key, value) }
        // Explicit settings batches must reach disk before Save/selection reports success.
        check(editor.commit()) { "Unable to persist settings" }
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
