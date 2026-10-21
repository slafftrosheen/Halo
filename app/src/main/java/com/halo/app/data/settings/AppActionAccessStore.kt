package com.halo.app.data.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Eligibility preferences only. Enabling an action never supplies execution approval. */
class AppActionAccessStore(context: Context, private val onChanged: () -> Unit = {}) {
    private val preferences = context.getSharedPreferences("app_action_access", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(preferences.getStringSet("disabled", emptySet()).orEmpty().toSet())
    val disabled = mutable.asStateFlow()
    private val revisionCounter = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = revisionCounter.get()

    fun allows(packageName: String, action: String): Boolean =
        key(packageName, action) !in mutable.value && key(packageName, "*") !in mutable.value

    suspend fun setAllowed(packageName: String, action: String, allowed: Boolean) = mutex.withLock {
        require(packageName.isNotBlank() && ':' !in packageName && action.isNotBlank() && ':' !in action)
        val key = key(packageName, action)
        val next = if (allowed) mutable.value - key else mutable.value + key
        withContext(Dispatchers.IO) {
            check(preferences.edit().putStringSet("disabled", next).commit()) { "Could not save app action access" }
            // Publish alongside the completed durable write, even if the caller leaves the page.
            mutable.value = next
            revisionCounter.incrementAndGet()
            // Eligibility is already durable and enforced by every dispatch path. A catalogue
            // refresh failure must not report that this saved access preference was lost.
            runCatching(onChanged)
        }
    }

    companion object {
        fun key(packageName: String, action: String) = "$packageName:$action"
    }
}
