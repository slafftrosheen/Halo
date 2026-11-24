package com.halo.app.ui.theme.v13

import com.halo.app.wallpaper.v13.Season
import com.halo.app.wallpaper.v13.SkyPhase
import com.halo.app.wallpaper.v13.SkyTimeline
import com.halo.app.wallpaper.v13.Sun
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationRuntimeTest {
    @Test fun skyPhasesAreReachableOnShortAndMidnightWrappingDays() {
        for (sun in listOf(Sun(LocalTime.of(11, 0), LocalTime.of(13, 0)),
            Sun(LocalTime.of(3, 0), LocalTime.of(0, 30)))) {
            val samples = (0 until 1440).map { SkyTimeline.phaseAt(LocalTime.MIDNIGHT.plusMinutes(it.toLong()), sun) }
            assertEquals(SkyPhase.entries.toSet(), samples.map { it.first }.toSet())
            assertTrue(samples.all { it.second in 0f..1f })
        }
    }

    @Test fun solarTimesVaryWithSeasonAndSouthernSeasonIsReversed() {
        val june = LocalDate.of(2026, 6, 21)
        val december = june.plusMonths(6)
        val zone = ZoneId.of("Europe/Riga")
        val summer = SkyTimeline.solarTimes(june, 56.95, 24.11, zone)
        val winter = SkyTimeline.solarTimes(december, 56.95, 24.11, zone)
        assertTrue(summer.rise < winter.rise && summer.set > winter.set)
        assertEquals(Season.WINTER, SkyTimeline.season(june, true))
    }

    @Test fun derivedWallpaperInkAndItsLabelPassOnLightAndDarkSurfaces() {
        val seeds = listOf(Rgb(250, 248, 242), Rgb(10, 10, 20), Rgb(249, 21, 56), Rgb(150, 125, 220))
        for ((dark, surface) in listOf(true to Rgb(43, 36, 83), false to Rgb(227, 233, 239))) {
            for (seed in seeds) {
                val result = LookEngine.derive(seed, surface, dark)
                assertTrue(contrast(result.accent, surface) >= 4.5)
                assertTrue(contrast(result.onAccent, result.accent) >= 4.5)
                assertTrue(result.accent != MeaningColours.ERROR)
            }
        }
    }
}
