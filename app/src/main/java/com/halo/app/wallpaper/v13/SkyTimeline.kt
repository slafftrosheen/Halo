package com.halo.app.wallpaper.v13

import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * halo 13.0 · the wallpaper follows the real sky.
 * Replaces the 3-step WallpaperPeriod (MORNING, DAY, NIGHT) with 6 phases + a blend factor,
 * driven by today's sunrise / sunset (weather source if allowed, else a latitude table, else fixed times).
 * Motion respects HaloLookMotion: FULL = slow drift, GENTLE = crossfade on unlock, STILL = change only on unlock, no animation.
 */
enum class SkyPhase { NIGHT, DAWN, MORNING, DAY, GOLDEN, DUSK }
enum class Season { WINTER, SPRING, SUMMER, AUTUMN }
enum class SkyWeather { CLEAR, CLOUDS, RAIN, SNOW, FOG }

data class Sun(val rise: LocalTime, val set: LocalTime)
data class SkyState(val phase: SkyPhase, val next: SkyPhase, val blend: Float, val season: Season, val weather: SkyWeather)

object SkyTimeline {
    private const val TWILIGHT_MIN = 40L

    fun phaseAt(now: LocalTime, sun: Sun): Pair<SkyPhase, Float> {
        val rise = sun.rise.toSecondOfDay() / 60f
        val daylight = ((sun.set.toSecondOfDay() - sun.rise.toSecondOfDay()) / 60f + 1440) % 1440
        val twilight = minOf(TWILIGHT_MIN.toFloat(), (1440 - daylight) / 3)
        val m = (now.toSecondOfDay() / 60f - rise + twilight + 1440) % 1440
        val edges = listOf(
            0f to SkyPhase.DAWN,
            twilight + minOf(60f, daylight * .15f) to SkyPhase.MORNING,
            twilight + minOf(180f, daylight * .38f) to SkyPhase.DAY,
            twilight + daylight - minOf(90f, daylight * .25f) to SkyPhase.GOLDEN,
            twilight + daylight to SkyPhase.DUSK,
            2 * twilight + daylight to SkyPhase.NIGHT)
        val i = edges.indexOfLast { m >= it.first }
        val start = edges[i].first; val end = edges.getOrNull(i + 1)?.first ?: 1440f
        return edges[i].second to ((m - start) / (end - start)).coerceIn(0f, 1f)
    }

    /** Estimate local solar times from a city the person already chose. */
    fun solarTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Sun {
        val angle = 2 * Math.PI * (date.dayOfYear - 1) / date.lengthOfYear()
        val declination = .006918 - .399912 * cos(angle) + .070257 * sin(angle) -
            .006758 * cos(2 * angle) + .000907 * sin(2 * angle) - .002697 * cos(3 * angle) + .00148 * sin(3 * angle)
        val correction = 229.18 * (.000075 + .001868 * cos(angle) - .032077 * sin(angle) -
            .014615 * cos(2 * angle) - .040849 * sin(2 * angle))
        val phi = Math.toRadians(latitude.coerceIn(-89.9, 89.9))
        val horizon = (sin(Math.toRadians(-.833)) / (cos(phi) * cos(declination)) -
            tan(phi) * tan(declination)).coerceIn(-1.0, 1.0)
        val halfDay = (4 * Math.toDegrees(acos(horizon))).coerceIn(5.0, 715.0)
        val offset = date.atTime(12, 0).atZone(zone).offset.totalSeconds / 60
        val noon = 720 - 4 * longitude - correction + offset
        fun local(minutes: Double) = LocalTime.MIDNIGHT.plusMinutes(minutes.roundToInt().toLong())
        return Sun(local(noon - halfDay), local(noon + halfDay))
    }

    fun season(date: LocalDate, southern: Boolean): Season {
        val north = when (date.month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
            Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
            Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }
        return if (!southern) north else Season.values()[(north.ordinal + 2) % 4]
    }

    fun state(now: LocalTime, date: LocalDate, sun: Sun, southern: Boolean, weather: SkyWeather): SkyState {
        val (p, blend) = phaseAt(now, sun)
        val next = SkyPhase.values()[(p.ordinal + 1) % SkyPhase.values().size]
        return SkyState(p, next, blend, season(date, southern), weather)
    }

    /** Fallback when no location is shared – Riga-like defaults, never asks for location just for a wallpaper. */
    val DEFAULT_SUN = Sun(LocalTime.of(7, 30), LocalTime.of(17, 30))
}
