package com.halo.app.wallpaper

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WallpaperOptions(
    val wallpaper: HaloWallpaper = HaloWallpaper.DRIFT,
    val inApp: Boolean = false,
    val reactsToHalo: Boolean = true,
    val calmerAtNight: Boolean = true,
    val schedule: WallpaperSchedule = WallpaperSchedule(),
    val photo: WallpaperPhoto? = null,
)

internal enum class WallpaperPeriod { MORNING, DAY, NIGHT }
internal enum class WallpaperBlur { LIGHT, SOFT, STRONG }
internal enum class WallpaperRing(val fraction: Float) { TOP(.23f), CENTER(.42f), LOWER(.67f) }

internal data class WallpaperSchedule(
    val enabled: Boolean = false,
    val morning: HaloWallpaper? = null,
    val day: HaloWallpaper? = null,
    val night: HaloWallpaper? = null,
) {
    operator fun get(period: WallpaperPeriod): HaloWallpaper? = when (period) {
        WallpaperPeriod.MORNING -> morning
        WallpaperPeriod.DAY -> day
        WallpaperPeriod.NIGHT -> night
    }

    fun with(period: WallpaperPeriod, wallpaper: HaloWallpaper?): WallpaperSchedule = when (period) {
        WallpaperPeriod.MORNING -> copy(morning = wallpaper)
        WallpaperPeriod.DAY -> copy(day = wallpaper)
        WallpaperPeriod.NIGHT -> copy(night = wallpaper)
    }
}

internal data class WallpaperPhoto(
    val id: String,
    val blur: WallpaperBlur = WallpaperBlur.SOFT,
    val ring: WallpaperRing = WallpaperRing.CENTER,
)

internal fun wallpaperPeriod(time: Float): WallpaperPeriod = when (time) {
    in (6f / 24f)..<(12f / 24f) -> WallpaperPeriod.MORNING
    in (12f / 24f)..<(20f / 24f) -> WallpaperPeriod.DAY
    else -> WallpaperPeriod.NIGHT
}

internal fun WallpaperOptions.wallpaperAt(period: WallpaperPeriod): HaloWallpaper =
    if (schedule.enabled) schedule[period] ?: wallpaper else wallpaper

/** Keep the current period while the surface is visible, even across a clock boundary. */
internal class WallpaperPeriodLatch(initial: WallpaperPeriod) {
    var period = initial
        private set
    private var hiddenSince: Long? = null

    fun update(actual: WallpaperPeriod, visible: Boolean, nowMillis: Long): WallpaperPeriod {
        val hiddenAt = hiddenSince
        if (hiddenAt != null && nowMillis - hiddenAt >= 2_000) period = actual
        hiddenSince = if (visible) null else hiddenAt ?: nowMillis
        return period
    }
}

internal data class WallpaperChange(val previous: WallpaperOptions, val appliedAt: Long) {
    fun canRestore(now: Long) = now - appliedAt in 0..604_800_000L
}

internal data class WallpaperSnapshot(
    val current: WallpaperOptions = WallpaperOptions(),
    val previous: WallpaperChange? = null,
)

/** A wallpaper edit and its one-step restore record are committed in the same transaction. */
internal class WallpaperPreferences private constructor(private val context: Context) {
    private val disk = context.getSharedPreferences("halo_wallpaper_options", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val mutable = MutableStateFlow(WallpaperSnapshot())
    val state = mutable.asStateFlow()
    private val loaded = scope.async {
        val saved = decode(disk.getString("current", null)) ?: WallpaperOptions()
        val previous = decode(disk.getString("previous", null))?.let {
            WallpaperChange(it, disk.getLong("applied_at", 0))
        }
        mutable.value = WallpaperSnapshot(saved, previous)
    }

    suspend fun apply(expected: WallpaperSnapshot, options: WallpaperOptions) = withContext(Dispatchers.IO) {
        loaded.await()
        writeLock.withLock {
            com.halo.app.ui.theme.HaloLayoutLock.get(context).requireUnlocked()
            check(mutable.value == expected) { "Wallpaper changed" }
            check(options.photo != null || !options.usesPhoto()) { "Choose a wallpaper photo first" }
            if (options != expected.current) save(WallpaperSnapshot(options,
                WallpaperChange(expected.current, System.currentTimeMillis())))
        }
    }

    suspend fun restore(expected: WallpaperSnapshot) = withContext(Dispatchers.IO) {
        loaded.await()
        writeLock.withLock {
            com.halo.app.ui.theme.HaloLayoutLock.get(context).requireUnlocked()
            val previous = expected.previous
            check(mutable.value == expected && previous?.canRestore(System.currentTimeMillis()) == true) { "Wallpaper changed" }
            save(WallpaperSnapshot(requireNotNull(previous).previous))
        }
    }

    private fun save(snapshot: WallpaperSnapshot) {
        check(disk.edit().putString("current", encode(snapshot.current))
            .putString("previous", snapshot.previous?.let { encode(it.previous) })
            .putLong("applied_at", snapshot.previous?.appliedAt ?: 0L).commit()) { "Wallpaper settings could not be saved" }
        mutable.value = snapshot
        WallpaperPhotoStore(context).trim(setOfNotNull(snapshot.current.photo?.id, snapshot.previous?.previous?.photo?.id))
    }

    private fun encode(option: WallpaperOptions) =
        listOf(option.wallpaper.name, option.inApp, option.reactsToHalo, option.calmerAtNight,
            option.schedule.enabled, option.schedule.morning?.name.orEmpty(), option.schedule.day?.name.orEmpty(),
            option.schedule.night?.name.orEmpty(), option.photo?.id.orEmpty(), option.photo?.blur?.name.orEmpty(),
            option.photo?.ring?.name.orEmpty()).joinToString("|")

    private fun decode(value: String?): WallpaperOptions? {
        val fields = value?.split('|') ?: return null
        if (fields.size !in setOf(4, 11)) return null
        val wallpaper = HaloWallpaper.entries.firstOrNull { it.name == fields[0] } ?: return null
        fun style(index: Int) = HaloWallpaper.entries.firstOrNull { it.name == fields.getOrNull(index) }
        val schedule = WallpaperSchedule(fields.getOrNull(4) == "true", style(5), style(6), style(7))
        val photo = fields.getOrNull(8)?.takeIf(::validWallpaperPhotoId)?.let { id ->
            WallpaperPhoto(id, WallpaperBlur.entries.firstOrNull { it.name == fields[9] } ?: WallpaperBlur.SOFT,
                WallpaperRing.entries.firstOrNull { it.name == fields[10] } ?: WallpaperRing.CENTER)
        }
        return WallpaperOptions(wallpaper, fields[1].toBooleanStrictOrNull() ?: return null,
            fields[2].toBooleanStrictOrNull() ?: return null, fields[3].toBooleanStrictOrNull() ?: return null, schedule, photo)
    }

    companion object {
        @Volatile private var instance: WallpaperPreferences? = null
        fun get(context: Context): WallpaperPreferences = instance ?: synchronized(this) {
            instance ?: WallpaperPreferences(context.applicationContext).also { instance = it }
        }
    }
}

internal fun WallpaperOptions.usesPhoto() =
    wallpaper == HaloWallpaper.YOUR_PHOTO || schedule.enabled && WallpaperPeriod.entries.any { schedule[it] == HaloWallpaper.YOUR_PHOTO }
