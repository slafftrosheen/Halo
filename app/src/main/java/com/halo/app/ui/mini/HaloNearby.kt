package com.halo.app.ui.mini

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import com.halo.app.ui.presence.staticResource
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
internal fun nearbyOrb(context: Context, size: Int, floating: Boolean = false): Bitmap {
    val scheme = nearbyScheme(context)
    val accent = when (scheme.primaryContainer.toArgb()) {
        0xFF8EC5FF.toInt() -> 1
        0xFFC9B8FF.toInt() -> 2
        0xFFFF9EB3.toInt() -> 3
        else -> 0
    }
    val dark = floating || scheme.background.luminance() < .5f
    val resource = com.halo.app.ui.presence.PresenceArtwork.IDLE.staticResource(dark, false, accent)
    val drawable = requireNotNull(androidx.core.content.ContextCompat.getDrawable(context, resource))
    val bitmap = Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    val canvas = Canvas(bitmap)
    if (floating) canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, bitmap.width / 2f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HaloNightInk.toArgb() })
    drawable.draw(canvas)
    return bitmap
}

internal fun nearbyBackground(width: Int, height: Int, density: Float, colour: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceIn(1, 1200), height.coerceIn(1, 1200), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bounds = RectF(density / 2, density / 2, bitmap.width - density / 2, bitmap.height - density / 2)
    canvas.drawRoundRect(bounds, 20*density, 20*density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
            intArrayOf(androidx.core.graphics.ColorUtils.blendARGB(colour, 0xFFB4E6D6.toInt(), .10f), colour),
            null, Shader.TileMode.CLAMP)
    })
    canvas.drawRoundRect(bounds, 20*density, 20*density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = androidx.core.graphics.ColorUtils.blendARGB(colour, 0xFF9A9EB5.toInt(), .28f)
        style = Paint.Style.STROKE; strokeWidth = density
    })
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
