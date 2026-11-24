package com.halo.app.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import com.halo.app.ui.theme.v13.LookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun wallpaperLookSeed(context: Context): Int? = withContext(Dispatchers.IO) {
    val options = WallpaperPreferences.get(context).state.value.current
    val manager = WallpaperManager.getInstance(context)
    val usesHalo = options.inApp || manager.wallpaperInfo?.serviceName == HaloLiveWallpaperService::class.java.name
    if (!usesHalo) {
        return@withContext if (Build.VERSION.SDK_INT >= 27)
            manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        else null
    }
    val time = haloWallpaperTime()
    val style = options.wallpaperAt(wallpaperPeriod(time))
    val photo = if (style == HaloWallpaper.YOUR_PHOTO) WallpaperPhotoStore(context).render(options.photo) else null
    val sample = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    try {
        HaloWallpaperPainter().paint(Canvas(sample), style, 0f, time, 0xff0cf2ba.toInt(), HaloAmbient.IDLE,
            1f, ripples = false, dim = 0f, photo = photo, ring = options.photo?.ring ?: WallpaperRing.CENTER,
            sky = if (options.followSky) WallpaperEnvironment().skyState() else null)
        val pixels = IntArray(64 * 64)
        sample.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        val seed = LookEngine.seed(pixels)
        Color.rgb(seed.r, seed.g, seed.b)
    } finally {
        sample.recycle()
    }
}
