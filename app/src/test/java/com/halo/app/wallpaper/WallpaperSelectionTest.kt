package com.halo.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSelectionTest {
    @Test fun gallerySelectionChangesTheCurrentSlotAndRetainsTheOtherSlots() {
        val paper = WallpaperOptions(wallpaper = HaloWallpaper.PAPER,
            schedule = WallpaperSchedule(enabled = true, morning = HaloWallpaper.PAPER, night = HaloWallpaper.PAPER))

        val changed = paper.chooseGalleryScene(HaloWallpaper.AURORA, WallpaperPeriod.NIGHT)

        assertTrue(changed.inApp)
        assertEquals(HaloWallpaper.AURORA, changed.wallpaperAt(WallpaperPeriod.NIGHT))
        assertEquals(HaloWallpaper.PAPER, changed.wallpaperAt(WallpaperPeriod.MORNING))
        assertEquals(HaloWallpaper.PAPER, changed.wallpaperAt(WallpaperPeriod.DAY))
    }

    @Test fun galleryChangesTheMainSceneWhenSchedulingIsOff() {
        val changed = WallpaperOptions(wallpaper = HaloWallpaper.PAPER)
            .chooseGalleryScene(HaloWallpaper.RAIN_GLASS, WallpaperPeriod.NIGHT)
        WallpaperPeriod.entries.forEach { assertEquals(HaloWallpaper.RAIN_GLASS, changed.wallpaperAt(it)) }
    }

    @Test fun directScheduleEditsUpdateTheSlotEvenWhenTheWallpaperStaysVisible() {
        val latch = WallpaperPeriodLatch(WallpaperPeriod.MORNING)
        assertEquals(WallpaperPeriod.MORNING, latch.update(WallpaperPeriod.DAY, visible = true, nowMillis = 100))
        assertEquals(WallpaperPeriod.DAY, latch.update(WallpaperPeriod.DAY, visible = true, nowMillis = 101,
            scheduleChanged = true))
    }
}
