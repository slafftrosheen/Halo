package com.halo.app.ui.mini

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.halo.app.MainActivity
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.app.ui.theme.*

/** Native hosts read the same stored profiles as Compose; call on an IO worker. */
internal fun nearbyScheme(context: Context): androidx.compose.material3.ColorScheme {
    val appearance = context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).getString("mode", "SYSTEM")
    val dark = when (appearance) { "DARK" -> true; "LIGHT" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
    val preferences = context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE)
    val prefix = if (dark) "dark" else "light"
    val accent = runCatching { HaloAccent.valueOf(preferences.getString("$prefix.accent", "MINT")!!) }.getOrDefault(HaloAccent.MINT)
    val tone = runCatching { HaloTone.valueOf(preferences.getString("$prefix.tone", "NATURAL")!!) }.getOrDefault(HaloTone.NATURAL)
    return haloColorScheme(dark, HaloPalette(accent, tone))
}

/** A static version of Halo's orbital ring for RemoteViews and the collapsed bubble. */
internal fun nearbyOrb(size: Int, accent: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    val radius = size * .35f
    paint.color = HaloNightInk.toArgb()
    canvas.drawCircle(center, center, size * .49f, paint)
    val (ringStart, ringEnd) = when (accent) {
        0xFF8EC5FF.toInt() -> 0xFF6FB4FF.toInt() to 0xFFC9E3FF.toInt()
        0xFFC9B8FF.toInt() -> 0xFFA98EFF.toInt() to 0xFFE2D9FF.toInt()
        0xFFFF9EB3.toInt() -> 0xFFFF7E9C.toInt() to 0xFFFFD0DB.toInt()
        else -> accent to HaloMintTint.toArgb()
    }
    paint.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
        ringStart, ringEnd, android.graphics.Shader.TileMode.CLAMP)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .075f; paint.strokeCap = Paint.Cap.ROUND
    canvas.drawArc(RectF(center-radius, center-radius, center+radius, center+radius), 337f, 316f, false, paint)
    return bitmap
}

internal fun nearbyBackground(width: Int, height: Int, density: Float, colour: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceIn(1, 1200), height.coerceIn(1, 1200), Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawRoundRect(RectF(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat()), 18*density,18*density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
    return bitmap
}

object MiniHaloEntry {
    const val EXTRA_DRAFT = "com.halo.app.extra.MINI_DRAFT"
    val pendingDraft = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun intent(context: Context, screen: String = "mini_halo", draft: String? = null): Intent =
        Intent(context, if (screen == "mini_halo") MiniHaloActivity::class.java else MainActivity::class.java).putExtra(HaloScreenCatalog.EXTRA_SCREEN, screen)
            .apply { draft?.let { putExtra(EXTRA_DRAFT, it.take(2048)) } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    fun pending(context: Context, code: Int, screen: String = "mini_halo", draft: String? = null): PendingIntent =
        PendingIntent.getActivity(context, code, intent(context, screen, draft), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
