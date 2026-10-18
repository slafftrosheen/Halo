package com.halo.app.ui.screens.kids

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Kids mode plus a parent-verified, process-local sharing grant; canonical provider/source policies still apply. */
class KidsPreferences(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val mutable = MutableStateFlow(false)
    private val ready = MutableStateFlow(false)
    val loadedState = ready.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val loadError = error.asStateFlow()
    val enabled = mutable.asStateFlow()
    private val cloud = MutableStateFlow(false)
    val cloudAllowed = cloud.asStateFlow()
    fun setCloudAllowedForSession(value: Boolean) { cloud.value = value }
    private fun disk() = context.getSharedPreferences("halo_kids_ui", Context.MODE_PRIVATE)
    private val loaded = scope.async {
        try { mutable.value = disk().getBoolean("enabled", false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = true; error.value = "Could not read Kids settings. Private mode stays on until a grown-up can save the settings." }
        finally { ready.value = true }
    }
    suspend fun getEnabled(): Boolean { loaded.await(); return enabled.value }
    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        loaded.await()
        writes.withLock {
            check(disk().edit().putBoolean("enabled", value).commit()) { "Could not save Kids mode" }
            mutable.value = value
            error.value = null
            if (!value) cloud.value = false
        }
    }
}
