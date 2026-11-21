package com.halo.app.ui.toolkit

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.halo.app.nativeactions.NativeDndController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class HaloFocusSession(
    val version: String,
    val startedAt: Long,
    val deadlineElapsed: Long,
    val durationMinutes: Int,
    val boot: Int,
    val quietRequested: Boolean,
    val quietApplied: Boolean = false,
) {
    fun left(now: Long = SystemClock.elapsedRealtime()) = (deadlineElapsed - now).coerceAtLeast(0)
}

internal class HaloFocusOwner private constructor(private val context: Context) {
    private val disk = context.getSharedPreferences("halo_focus_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val boot get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun read(): HaloFocusSession? {
        val id = disk.getString("id", null) ?: return null
        val minutes = disk.getInt("minutes", 0)
        if (minutes !in 1..120) return null
        return HaloFocusSession(id, disk.getLong("started", 0), disk.getLong("deadline", 0), minutes,
            disk.getInt("boot", -1), disk.getBoolean("quiet", false), disk.getBoolean("quietApplied", false))
    }

    suspend fun current(): HaloFocusSession? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = mutable.value ?: return@withLock null
            if (session.boot == boot && session.left() > 0) session
            else { stopLocked(session); null }
        }
    }

    suspend fun start(minutes: Int, quiet: Boolean): HaloFocusSession = withContext(Dispatchers.IO) {
        require(minutes in 1..120)
        mutex.withLock {
            val family = requireAdultToolkit(context)
            val former = mutable.value
            check(former == null || former.boot != boot || former.left() == 0L)
            former?.let(::stopLocked)
            val duration = TimeUnit.MINUTES.toMillis(minutes.toLong())
            val requested = quiet && Build.VERSION.SDK_INT >= 29
            val session = HaloFocusSession(UUID.randomUUID().toString(), System.currentTimeMillis(),
                SystemClock.elapsedRealtime() + duration, minutes, boot, requested)
            // Schedule recovery before enabling an app-owned interruption rule.
            val work = OneTimeWorkRequestBuilder<HaloFocusEndWorker>()
                .setInputData(workDataOf("version" to session.version))
                .setInitialDelay(duration, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(workKey(session.version),
                ExistingWorkPolicy.KEEP, work).result.get()
            requireAdultToolkit(context, family)
            check(disk.edit().putString("id", session.version).putLong("started", session.startedAt)
                .putLong("deadline", session.deadlineElapsed).putInt("minutes", minutes).putInt("boot", session.boot)
                .putBoolean("quiet", requested).commit())
            mutable.value = session
            val applied = if (requested) runCatching { NativeDndController(context, focus = true).request(true).acknowledged }
                .getOrDefault(false) else false
            if (applied) {
                disk.edit().putBoolean("quietApplied", true).apply()
                mutable.value = session.copy(quietApplied = true)
            }
            com.halo.app.nativeactions.EverydayWidgetUpdates.enqueue(context)
            requireNotNull(mutable.value)
        }
    }

    suspend fun end(version: String, onlyExpired: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = mutable.value?.takeIf { it.version == version } ?: return@withLock false
            if (onlyExpired && session.boot == boot && session.left() > 0) return@withLock false
            stopLocked(session)
            true
        }
    }

    private fun stopLocked(session: HaloFocusSession) {
        if (session.quietRequested) {
            try { NativeDndController(context, focus = true).request(false) }
            catch (_: SecurityException) { /* Android owns access and removal of revoked rules. */ }
        }
        check(disk.edit().clear().commit()) { "Could not end focus" }
        mutable.value = null
        com.halo.app.nativeactions.EverydayWidgetUpdates.enqueue(context)
    }

    companion object {
        @Volatile private var instance: HaloFocusOwner? = null
        fun get(context: Context): HaloFocusOwner = instance ?: synchronized(this) {
            instance ?: HaloFocusOwner(context.applicationContext).also { instance = it }
        }
        private fun workKey(version: String) = "halo-focus-end-$version"
    }
}

class HaloFocusEndWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("version") ?: return Result.failure()
        return try {
            val owner = HaloFocusOwner.get(applicationContext)
            val ended = owner.end(id, onlyExpired = true)
            if (!ended && owner.state.value?.version == id) Result.retry() else Result.success()
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
