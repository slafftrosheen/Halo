package com.halo.app.data.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DigestDeliverySettings(val channel: String = "activity", val wearableId: String = "")

interface DigestDeliverySettingsRepository {
    val state: StateFlow<DigestDeliverySettings>
    suspend fun setChannel(value: String)
    suspend fun setWearableId(value: String)
}

class SharedPreferencesDigestDeliverySettingsRepository(
    context: Context,
    scope: CoroutineScope,
) : DigestDeliverySettingsRepository {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow(DigestDeliverySettings())
    override val state: StateFlow<DigestDeliverySettings> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val prefs = appContext.getSharedPreferences("halo_digest_delivery", Context.MODE_PRIVATE)
                _state.value = DigestDeliverySettings(
                    prefs.getString("channel", "activity") ?: "activity",
                    prefs.getString("device", "") ?: "",
                )
            }
        }
    }

    override suspend fun setChannel(value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = appContext.getSharedPreferences("halo_digest_delivery", Context.MODE_PRIVATE)
            check(prefs.edit().putString("channel", value).commit()) { "Unable to persist digest delivery channel" }
            _state.value = _state.value.copy(channel = value)
        }
    }

    override suspend fun setWearableId(value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = appContext.getSharedPreferences("halo_digest_delivery", Context.MODE_PRIVATE)
            check(prefs.edit().putString("device", value).commit()) { "Unable to persist digest wearable" }
            _state.value = _state.value.copy(wearableId = value)
        }
    }
}
