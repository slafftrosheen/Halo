package com.halo.app.wallpaper

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerRecord
import com.halo.app.everyday.organizer.OrganizerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class WallpaperSeason { WINTER, SPRING, SUMMER, AUTUMN }

internal data class WallpaperEnvironment(
    val raining: Boolean = false,
    val snowing: Boolean = false,
    val completedToday: Int = 0,
    val wind: Float = 0f,
    val season: WallpaperSeason = wallpaperSeason(LocalDate.now(), null),
)

private fun wallpaperSeason(date: LocalDate, southern: Boolean?): WallpaperSeason {
    val month = if (southern == true) (date.monthValue + 5) % 12 + 1 else date.monthValue
    return when (month) {
        12, 1, 2 -> WallpaperSeason.WINTER
        in 3..5 -> WallpaperSeason.SPRING
        in 6..8 -> WallpaperSeason.SUMMER
        else -> WallpaperSeason.AUTUMN
    }
}

/** Read existing local snapshots only and export decorative signals without names or record text. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun wallpaperEnvironment(weather: Boolean, garden: Boolean, kidsSky: Boolean = false): Flow<WallpaperEnvironment> {
    val family = Graph.kidsPreferences
    return combine(family.loadedState, family.enabled) { loaded, kids -> loaded && kids == kidsSky }
        .distinctUntilChanged()
        .flatMapLatest { allowed ->
            if (!allowed || (!weather && !garden)) return@flatMapLatest flowOf(WallpaperEnvironment())
            val cachedWeather: Flow<List<CollectorState>> = if (weather) Graph.collectorRepository.observe() else flowOf(emptyList())
            val organizer: Flow<List<OrganizerRecord>> = if (garden && !kidsSky) Graph.organizerRepository.observe() else flowOf(emptyList())
            val childCompletions = if (garden && kidsSky) combine(family.pictureTools.ticks, family.pictureTools.day) { ticks, rows ->
                ticks.date to rows.count { it.id in ticks.ids }
            } else flowOf(null to 0)
            val now = flow {
                while (true) {
                    emit(Instant.now())
                    delay(60_000)
                }
            }
            combine(cachedWeather, organizer, childCompletions, now) { sources, records, child, instant ->
                val current = sources.asSequence().filter { it.source.selected && it.source.enabled && !it.source.muted }
                    .filter { !kidsSky || it.source.childApproved }
                    .mapNotNull { state ->
                        state.snapshot?.takeIf { it.sourceVersion == state.source.version && it.expiresAt >= instant }
                    }.sortedByDescending { it.verifiedAt }
                    .mapNotNull { (it.payload as? CollectorPayload.Weather)?.fact }.firstOrNull()
                val zone = ZoneId.systemDefault()
                val today = instant.atZone(zone).toLocalDate()
                val completed = if (kidsSky) child.second.takeIf { child.first == today } ?: 0
                    else records.count { it.kind == OrganizerKind.TASK && it.status == OrganizerStatus.COMPLETED &&
                        it.deletedAt == null && it.updatedAt.atZone(zone).toLocalDate() == today }
                val code = current?.weatherCode ?: 0
                WallpaperEnvironment(
                    raining = code in 51..67 || code in 80..82 || code in 95..99,
                    snowing = code in 71..77 || code in 85..86,
                    completedToday = completed.coerceAtMost(12),
                    wind = (current?.windKph?.toFloat()?.div(65f) ?: 0f).coerceIn(0f, 1f),
                    season = wallpaperSeason(today, current?.place?.latitude?.let { it < 0 }),
                )
            }
        }.distinctUntilChanged()
        .catch { emit(WallpaperEnvironment()) }
}
