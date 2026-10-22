package com.halo.app.lab.bridge

import android.content.Context

/** Halo-owned, explicit opt-in. A bridge request can never grant or widen this consent. */
class HaloLabConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean): Boolean = preferences.edit()
        .putBoolean(KEY_ENABLED, enabled)
        .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        .commit()

    fun updatedAtEpochMs(): Long? = preferences.getLong(KEY_UPDATED_AT, 0L).takeIf { it > 0L }

    companion object {
        private const val PREFERENCES = "halo_lab_bridge_consent_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UPDATED_AT = "updated_at_epoch_ms"
    }
}
