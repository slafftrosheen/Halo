package com.halo.app.privacy

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal enum class HaloPauseLength {
    HALF_HOUR, ONE_HOUR, UNTIL_TOMORROW;

    fun end(now: Instant, zone: ZoneId): Instant = when (this) {
        HALF_HOUR -> now.plusSeconds(1_800)
        ONE_HOUR -> now.plusSeconds(3_600)
        UNTIL_TOMORROW -> now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    }
}

internal data class HaloPauseSnapshot(
    val version: String,
    val untilMillis: Long = 0L,
    val untilElapsedMillis: Long = 0L,
    val boot: Int = -1,
) {
    val paused get() = untilMillis != 0L
}

/** Phone-owned, durable pause. It never rewrites provider, device, or Organizer preferences. */
internal class HaloPauseOwner private constructor(private val context: Context) {
    private val disk = context.getSharedPreferences("halo_global_pause", Context.MODE_PRIVATE)
    private val lock = Any()
    private val work = mutableSetOf<Job>()
    private val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
        .getOrDefault(-1)
    private val mutable = MutableStateFlow(HaloPauseSnapshot(disk.getString("version", "active") ?: "active",
        disk.getLong("until", 0L), disk.getLong("elapsed", 0L), disk.getInt("boot", -1)))
    val state = mutable.asStateFlow()

    fun remaining(snapshot: HaloPauseSnapshot = state.value): Long {
        if (!snapshot.paused) return 0
        val left = if (boot >= 0 && snapshot.boot == boot) snapshot.untilElapsedMillis - SystemClock.elapsedRealtime()
        else snapshot.untilMillis - System.currentTimeMillis()
        return left.coerceIn(0L, maxPauseMillis)
    }

    // Expiration first clears the durable state via the owner, keeping admission and presentation in sync.
    fun isPaused() = state.value.paused

    fun requireActive() {
        if (isPaused()) throw HaloPausedCancellation()
    }

    suspend fun <T> whileActive(action: suspend () -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        synchronized(lock) {
            requireActive()
            work.add(job)
        }
        try {
            val result = action()
            currentCoroutineContext().ensureActive()
            requireActive()
            result
        } finally {
            synchronized(lock) { work.remove(job) }
        }
    }

    suspend fun pause(expected: String, length: HaloPauseLength, authorize: () -> Unit): HaloPauseSnapshot =
        withContext(Dispatchers.IO) {
            val (snapshot, active) = synchronized(lock) {
                authorize()
                check(state.value.version == expected)
                val now = Instant.now()
                val end = length.end(now, ZoneId.systemDefault()).toEpochMilli()
                val elapsed = SystemClock.elapsedRealtime() + (end - now.toEpochMilli()).coerceIn(1L, maxPauseMillis)
                val next = HaloPauseSnapshot(UUID.randomUUID().toString(), end, elapsed, boot)
                save(next)
                next to work.toList()
            }
            active.forEach { it.cancel(HaloPausedCancellation()) }
            snapshot
        }

    suspend fun resume(expected: String, authorize: () -> Unit): HaloPauseSnapshot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            authorize()
            check(state.value.paused && state.value.version == expected)
            HaloPauseSnapshot(UUID.randomUUID().toString()).also(::save)
        }
    }

    suspend fun expire(expected: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = state.value
            if (current.version == expected && current.paused && remaining(current) == 0L) {
                save(HaloPauseSnapshot(UUID.randomUUID().toString()))
            }
        }
    }

    private fun save(value: HaloPauseSnapshot) {
        check(disk.edit().putString("version", value.version).putLong("until", value.untilMillis)
            .putLong("elapsed", value.untilElapsedMillis).putInt("boot", value.boot).commit()) { "Pause setting could not be saved" }
        mutable.value = value
    }

    companion object {
        private const val maxPauseMillis = 26 * 60 * 60 * 1000L
        @Volatile private var instance: HaloPauseOwner? = null

        fun get(context: Context): HaloPauseOwner = instance ?: synchronized(this) {
            instance ?: HaloPauseOwner(context.applicationContext).also { instance = it }
        }
    }
}

internal class HaloPausedCancellation : CancellationException("Halo is paused")
