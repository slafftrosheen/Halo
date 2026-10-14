package com.halo.app.ui.screens.kids

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Presentation mode only; never changes tool approvals, permissions or provider policy. */
class KidsPreferences(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val mutable = MutableStateFlow(false)
    val enabled = mutable.asStateFlow()
    private fun disk() = context.getSharedPreferences("halo_kids_ui", Context.MODE_PRIVATE)
    private val loaded = scope.async { mutable.value = disk().getBoolean("enabled", false) }
    suspend fun getEnabled(): Boolean { loaded.await(); return enabled.value }
    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        loaded.await()
        writes.withLock {
            check(disk().edit().putBoolean("enabled", value).commit()) { "Could not save Kids mode" }
            mutable.value = value
        }
    }
}
