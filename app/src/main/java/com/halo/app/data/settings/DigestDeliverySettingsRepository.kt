package com.halo.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DigestDeliverySettings(
    val channel: String = "activity",
    val wearableId: String = "",
)

interface DigestDeliverySettingsRepository {
    val state: StateFlow<DigestDeliverySettings>
    suspend fun setChannel(value: String)
    suspend fun setWearableId(value: String)
}

class SharedPreferencesDigestDeliverySettingsRepository(
    context: Context
) : DigestDeliverySettingsRepository {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("halo_digest_delivery", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        DigestDeliverySettings(
            channel = prefs.getString("channel", "activity") ?: "activity",
            wearableId = prefs.getString("device", "") ?: ""
        )
    )
    override val state = _state.asStateFlow()

    override suspend fun setChannel(value: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString("channel", value).apply()
            _state.value = _state.value.copy(channel = value)
        }
    }

    override suspend fun setWearableId(value: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString("device", value).apply()
            _state.value = _state.value.copy(wearableId = value)
        }
    }
}
