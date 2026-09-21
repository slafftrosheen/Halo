package com.halo.app.ui.theme

import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Process-local appearance state.
 *
 * Construction is intentionally disk-free because this object is first touched by
 * the initial Compose tree. Persistence is loaded/written on IO and the StateFlow
 * updates immediately for user actions.
 */
class AppearancePreferences(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writes = Mutex()
    private val mutationGeneration = AtomicLong(0L)

    private val mutableMode = MutableStateFlow(AppearanceMode.SYSTEM)
    val mode: StateFlow<AppearanceMode> = mutableMode.asStateFlow()

    private val initialGeneration = mutationGeneration.get()
    private val initialLoad: Deferred<Unit> = scope.async {
        val restored = readModeFromDisk()
        if (mutationGeneration.get() == initialGeneration) {
            mutableMode.value = restored
        }
    }

    @Volatile
    private var persistenceJob: Job? = null

    fun setMode(mode: AppearanceMode) {
        if (mutableMode.value == mode) return

        mutationGeneration.incrementAndGet()
        mutableMode.value = mode

        persistenceJob = scope.launch {
            writes.withLock {
                check(
                    preferences()
                        .edit()
                        .putString(KEY_MODE, mode.name)
                        .commit()
                ) { "Could not persist Halo appearance mode" }
            }
        }
    }

    suspend fun reloadFromDisk() {
        val generation = mutationGeneration.incrementAndGet()
        val restored = withContext(ioDispatcher) { readModeFromDisk() }
        if (mutationGeneration.get() == generation) {
            mutableMode.value = restored
        }
    }

    internal suspend fun awaitLoaded() {
        initialLoad.await()
    }

    internal suspend fun awaitPersistence() {
        persistenceJob?.join()
    }

    private fun readModeFromDisk(): AppearanceMode {
        val stored = preferences().getString(KEY_MODE, null)
        return stored
            ?.let { value -> runCatching { AppearanceMode.valueOf(value) }.getOrNull() }
            ?: AppearanceMode.SYSTEM
    }

    private fun preferences() =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFERENCES_NAME = "halo_appearance"
        const val KEY_MODE = "mode"
    }
}
