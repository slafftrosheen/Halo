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
        seed("catalogue-v1") { StarterSourceCatalogue.local() + StarterSourceCatalogue.firstEdition() }
        seed("catalogue-v2", StarterSourceCatalogue::expandedEdition)
        ready = true
    }

    private suspend fun seed(version: String, sources: () -> List<CollectorSource>) {
        if (preferences.getBoolean(version, false)) return
        sources().forEach { source ->
            if (repository.state(source.id) == null) repository.save(source, null)
        }
        check(preferences.edit().putBoolean(version, true).commit()) { "Could not save source setup" }
    }
}
