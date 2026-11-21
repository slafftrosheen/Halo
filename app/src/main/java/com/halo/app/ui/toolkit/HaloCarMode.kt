package com.halo.app.ui.toolkit

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Android owns the current car mode. Halo remembers only the user's startup preference. */
internal object HaloCarMode {
    private const val DISK = "halo_on_move"
    private const val AUTO_OPEN = "open_with_system_car_mode"
    private val writes = Mutex()

    suspend fun autoOpen(context: Context): Boolean = withContext(Dispatchers.IO) {
        context.getSharedPreferences(DISK, Context.MODE_PRIVATE).getBoolean(AUTO_OPEN, false)
    }

    suspend fun setAutoOpen(context: Context, current: Boolean, enabled: Boolean) {
        val family = ManualDependencyGraph.kidsPreferences
        val revision = family.adultActionRevision()
        withContext(Dispatchers.IO) {
            writes.withLock {
                val disk = context.getSharedPreferences(DISK, Context.MODE_PRIVATE)
                check(disk.getBoolean(AUTO_OPEN, false) == current)
                family.requireAdultAction(revision)
                check(disk.edit().putBoolean(AUTO_OPEN, enabled).commit())
            }
        }
    }

    fun active(context: Context): Boolean =
        context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
}
