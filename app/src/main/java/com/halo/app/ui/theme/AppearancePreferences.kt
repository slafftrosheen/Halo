package com.halo.app.ui.theme

import android.content.Context
import android.util.Log
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
    private val shortcutGeneration = AtomicLong(0L)
    private val mutableOrbShortcuts = MutableStateFlow(com.halo.app.ui.presence.HaloOrbShortcut.defaults)
    val orbShortcuts: StateFlow<Set<com.halo.app.ui.presence.HaloOrbShortcut>> = mutableOrbShortcuts.asStateFlow()
    private val initialShortcutGeneration = shortcutGeneration.get()

    private val initialGeneration = mutationGeneration.get()
    private val initialLoad: Deferred<Unit> = scope.async {
        val restored = readModeFromDisk()
        if (mutationGeneration.get() == initialGeneration) {
            mutableMode.value = restored
        }
        val restoredShortcuts = com.halo.app.ui.presence.HaloOrbShortcut.decode(preferences().getStringSet(KEY_ORB_SHORTCUTS, null))
        if (shortcutGeneration.get() == initialShortcutGeneration) mutableOrbShortcuts.value = restoredShortcuts
    }

    @Volatile
    private var persistenceJob: Job? = null
    @Volatile private var shortcutPersistenceJob: Job? = null

    fun setOrbShortcuts(shortcuts: Set<com.halo.app.ui.presence.HaloOrbShortcut>) {
        val bounded = shortcuts.toSet()
        val generation = shortcutGeneration.incrementAndGet()
        mutableOrbShortcuts.value = bounded
        shortcutPersistenceJob = scope.launch {
            writes.withLock {
                if (shortcutGeneration.get() != generation) return@withLock
                if (!preferences().edit().putStringSet(KEY_ORB_SHORTCUTS, bounded.map { it.name }.toSet()).commit())
                    Log.w(TAG, "Could not persist Halo shortcut preferences")
            }
        }
    }

    fun setMode(mode: AppearanceMode) {
        // The initial SYSTEM value is only a placeholder until disk has loaded.
        if (initialLoad.isCompleted && mutableMode.value == mode) return

        val generation = mutationGeneration.incrementAndGet()
        mutableMode.value = mode

        persistenceJob = scope.launch {
            writes.withLock {
                if (mutationGeneration.get() != generation) return@withLock
                val persisted = preferences()
                    .edit()
                    .putString(KEY_MODE, mode.name)
                    .commit()
                if (!persisted) {
                    Log.w(TAG, "Could not persist Halo appearance mode")
                }
                else com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(appContext)
            }
        }
    }

    suspend fun setModePersisted(mode: AppearanceMode) = withContext(ioDispatcher) {
        initialLoad.await()
        writes.withLock {
            check(preferences().edit().putString(KEY_MODE, mode.name).commit()) { "Could not persist Halo appearance mode" }
            mutationGeneration.incrementAndGet()
            mutableMode.value = mode
            com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(appContext)
        }
    }

    suspend fun reloadFromDisk() {
        val generation = mutationGeneration.incrementAndGet()
        val shortcutReload = shortcutGeneration.incrementAndGet()
        val restored = withContext(ioDispatcher) { readModeFromDisk() }
        if (mutationGeneration.get() == generation) {
            mutableMode.value = restored
        }
        val restoredShortcuts = withContext(ioDispatcher) {
            com.halo.app.ui.presence.HaloOrbShortcut.decode(preferences().getStringSet(KEY_ORB_SHORTCUTS, null))
        }
        if (shortcutGeneration.get() == shortcutReload) mutableOrbShortcuts.value = restoredShortcuts
    }

    internal suspend fun awaitLoaded() {
        initialLoad.await()
    }

    internal suspend fun awaitPersistence() {
        persistenceJob?.join()
        shortcutPersistenceJob?.join()
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
        const val TAG = "HaloAppearance"
        const val PREFERENCES_NAME = "halo_appearance"
        const val KEY_MODE = "mode"
        const val KEY_ORB_SHORTCUTS = "orb_shortcuts"
    }
}
