package com.halo.app.everyday.collectors.android

import android.content.Context
import com.halo.app.everyday.collectors.*
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Crash-resumable catalogue seed. Once completed, removed starters stay removed. */
class CollectorBootstrap(context: Context, private val repository: CollectorRepository) {
    private val preferences = context.getSharedPreferences("everyday-catalogue", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var ready = false
    suspend fun ensureInitialized() = lock.withLock {
        if (ready) return@withLock
        repository.recoverInterrupted(Instant.now())
        if (!preferences.getBoolean("catalogue-v1", false)) {
            (StarterSourceCatalogue.local() + StarterSourceCatalogue.external()).forEach { source ->
                if (repository.state(source.id) == null) repository.save(source, null)
            }
            check(preferences.edit().putBoolean("catalogue-v1", true).commit()) { "Could not save source setup" }
        }
        ready = true
    }
}
