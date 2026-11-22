package com.halo.app.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal class ToolkitWallpaperScenes {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val frame = RectF()
    private val palette = intArrayOf(0xFF0CF2BA.toInt(), 0xFFF3C0D9.toInt(), 0xFFFFE08A.toInt(),
        0xFFB6DFE2.toInt(), 0xFFC9B8FF.toInt(), 0xFFFF9F6B.toInt())

    fun paint(c: Canvas, wallpaper: HaloWallpaper, h: Float, t: Float, time: Float, accent: Int, state: HaloAmbient,
        environment: WallpaperEnvironment, photo: Bitmap?, ringPosition: WallpaperRing) {
        val signal = when (state) {
            HaloAmbient.NEEDS_YOU -> 0xFFFF9F6B.toInt()
            HaloAmbient.WORKING, HaloAmbient.LISTENING -> 0xFF0CF2BA.toInt()
            HaloAmbient.IDLE -> null
        }
        when (wallpaper) {
            HaloWallpaper.NORTHERN_FOREST -> forest(c, h, t, signal)
            HaloWallpaper.BALTIC_DUNES -> dunes(c, h, t, signal, environment.wind)
            HaloWallpaper.RAIN_GLASS -> rain(c, h, t, signal ?: accent, environment.raining)
            HaloWallpaper.LUMEN_GARDEN -> garden(c, h, t, signal ?: accent, environment.completedToday)
            HaloWallpaper.NIGHT_TRAIN -> train(c, h, t, time, signal ?: accent)
            HaloWallpaper.SEASONS -> seasons(c, h, t, environment)
            HaloWallpaper.RIGA_ROOFTOPS -> rooftops(c, h, t, time, signal)
            HaloWallpaper.DEEP_SEA -> deepSea(c, h, t, signal ?: palette[0])
            HaloWallpaper.PAPER_CUT -> paperCut(c, h, signal)
            HaloWallpaper.YOUR_PHOTO -> photo(c, h, photo, signal, ringPosition)
            HaloWallpaper.MEADOW_WIND -> meadow(c, h, t, signal, environment.wind)
            HaloWallpaper.INK_WASH -> inkWash(c, h, time, signal)
            else -> Unit
        }
    }

    private fun forest(c: Canvas, h: Float, t: Float, signal: Int?) {
        sky(c, h, 0xFF071A2B.toInt(), 0xFF08161C.toInt())
        val moon = signal ?: 0xFFDCFFF0.toInt()
        glow(c, 270f, h * .20f, 150f, moon, .24f)
        ring(c, 270f, h * .20f, 41f, moon, 8f)
        val trees = intArrayOf(0xFF12343A.toInt(), 0xFF0E2A30.toInt(), 0xFF0A2026.toInt(), 0xFF061418.toInt())
        trees.forEachIndexed { layer, color ->
            val ground = h * (.55f + layer * .125f)
            val stride = 31 - layer * 4
            path.rewind()
            path.moveTo(0f, h)
            for (x in -30..420 step stride) {
                val height = minOf(h * .34f, 52f + rand(x + layer * 7) * 90 + layer * 12)
                path.lineTo(x.toFloat(), ground)
                path.lineTo(x + stride / 2f, ground - height)
                path.lineTo(x + stride.toFloat(), ground)
            }
            path.lineTo(390f, h)
            path.close()
            paintPath(c, color)
            glow(c, 170f + sin(t * .16f + layer) * 83f, ground - 17, 132f, 0xFFB4DCE6.toInt(), .07f)
        }
    }

    private fun dunes(c: Canvas, h: Float, t: Float, signal: Int?, wind: Float) {
        sky(c, h, 0xFFF6E7D2.toInt(), 0xFFD6B48C.toInt())
        glow(c, 110f, h * .18f, 140f, 0xFFFFF0DC.toInt(), .8f)
        ring(c, 110f, h * .18f, 37f, signal ?: 0xFF24412F.toInt(), 8f)
        for (i in 0..8) {
            path.rewind()
            val y = h * (.42f + i * .066f)
            path.moveTo(0f, y)
            for (x in 0..390 step 10) path.lineTo(x.toFloat(), y + sin(x * .018f + i * .9f + t * (.08f + wind * .13f)) * (7 + i * 2))
            trace(c, withAlpha(0xFF785032.toInt(), .20f + i * .035f), 2f + i * .32f)
        }
        for (i in 0..23) {
            val x = rand(i) * 390
            val y = h * (.5f + rand(i + 3) * .19f)
            segment(c, x, y, x + sin(t * .4f + i) * wind * 4, y - 9 - rand(i + 5) * 13, 0x885C6642.toInt(), 1.5f)
        }
    }

    private fun rain(c: Canvas, h: Float, t: Float, accent: Int, raining: Boolean) {
        sky(c, h, 0xFF1B2134.toInt(), 0xFF2A3348.toInt())
        for (i in 0..13) glow(c, rand(i * 7) * 390, h * (.30f + rand(i + 10) * .54f),
            30 + rand(i + 78) * 54, palette[i % palette.size], .27f)
        ring(c, 195f, h * .30f, 61f, withAlpha(accent, .65f), 11f)
        for (i in 0..61) {
            val x = rand(i + 1) * 390
            val y = (rand(i + 2) * h + if (raining) t * (5 + rand(i + 29) * 19) else 0f) % h
            val r = 2f + rand(i + 4) * 5
            oval(c, x, y, r * .8f, r, 0x36FFFFFF)
            oval(c, x - r * .23f, y - r * .31f, r * .25f, r * .26f, 0x80FFFFFF.toInt())
            if (raining && i % 4 == 0) segment(c, x, y - r, x + rand(i) * 4, y - r - 22 - rand(i + 9) * 55, 0x26FFFFFF, r * .5f)
        }
    }

    private fun garden(c: Canvas, h: Float, t: Float, accent: Int, completed: Int) {
        c.drawColor(0xFF0A0726.toInt())
        glow(c, 195f, h * .43f, 212f, palette[4], .19f)
        ring(c, 195f, h * .43f, 100f, withAlpha(accent, .52f), 17f)
        for (i in 0..11) {
            val a = (-PI / 2 + i * PI / 6).toFloat()
            val x = 195 + 141 * cos(a) + sin(t * .8f + i) * 1.5f
            val y = h * .43f + minOf(171f, h * .34f) * sin(a)
            segment(c, x, y + 25, x, y, withAlpha(accent, .45f), 2f)
            val color = palette[i % palette.size]
            if (i < completed) {
                glow(c, x, y, 31f, color, .40f)
                for (petal in 0..4) {
                    val angle = (petal * 2 * PI / 5).toFloat()
                    oval(c, x + cos(angle) * 7, y + sin(angle) * 7, 5f, 4f, color)
                }
                oval(c, x, y, 2.8f, 2.8f, Color.WHITE)
            } else oval(c, x, y, 3.6f, 6.8f, withAlpha(color, .53f))
        }
    }

    private fun train(c: Canvas, h: Float, t: Float, time: Float, accent: Int) {
        sky(c, h, 0xFF05031C.toInt(), 0xFF120C3A.toInt())
        for (i in 0..52) oval(c, rand(i + 1) * 390, rand(i + 2) * h * .48f, .8f, .8f, withAlpha(Color.WHITE, .18f + rand(i) * .55f))
        ring(c, 300f, h * .16f, 29f, 0xBFFFFFFF.toInt(), 7f)
        path.rewind()
        path.moveTo(0f, h)
        for (x in 0..400 step 20) path.lineTo(x.toFloat(), h * .63f - rand(x) * minOf(h * .15f, 36f))
        path.lineTo(390f, h)
        path.close()
        paintPath(c, 0xFF07051A.toInt())
        for (i in 0..14) {
            val y = h * (.48f + rand(i + 7) * .16f)
            val commuteSpeed = if (time in .68f..(.89f)) 1.2f else .8f
            val x = (rand(i + 11) * 570 + t * (19 + rand(i) * 17) * commuteSpeed) % 570 - 140
            val length = 30 + rand(i) * 91
            val color = if (i % 4 == 0) accent else palette[2]
            line.color = Color.WHITE
            line.shader = LinearGradient(x, y, x + length, y, withAlpha(color, 0f), color, Shader.TileMode.CLAMP)
            line.strokeWidth = 2.5f
            c.drawLine(x, y, x + length, y, line)
            line.shader = null
        }
        fill.color = 0x0FFFFFFF
        c.drawRect(0f, 0f, 390f, h * .44f, fill)
    }

    private fun seasons(c: Canvas, h: Float, t: Float, env: WallpaperEnvironment) {
        val colors = when (env.season) {
            WallpaperSeason.WINTER -> intArrayOf(0xFF1A2440.toInt(), 0xFF4D5F80.toInt(), 0xFFE8EEF7.toInt(), 0xFF2A2A38.toInt())
            WallpaperSeason.SPRING -> intArrayOf(0xFFBFE3D8.toInt(), 0xFFF3F0D9.toInt(), 0xFF7FB36A.toInt(), 0xFFF3C0D9.toInt())
            WallpaperSeason.SUMMER -> intArrayOf(0xFF8FD0E8.toInt(), 0xFFF8E7B8.toInt(), 0xFF5F9A4F.toInt(), 0xFF3F8A4A.toInt())
            WallpaperSeason.AUTUMN -> intArrayOf(0xFF2D2340.toInt(), 0xFFC27A4A.toInt(), 0xFF6B4A2A.toInt(), 0xFFE8833D.toInt())
        }
        sky(c, h, colors[0], colors[1])
        ring(c, 290f, h * .15f, 30f, 0xDCFFFFFF.toInt(), 7f)
        fill.color = colors[2]
        c.drawRect(0f, h * .78f, 390f, h, fill)
        fun branch(x: Float, y: Float, angle: Float, length: Float, depth: Int) {
            val x2 = x + cos(angle) * length
            val y2 = y + sin(angle) * length
            segment(c, x, y, x2, y2, 0xFF372D2A.toInt(), maxOf(1f, 11f - depth * 1.6f))
            if (depth < 6) {
                branch(x2, y2, angle - .42f, length * .74f, depth + 1)
                branch(x2, y2, angle + .38f, length * .72f, depth + 1)
            }
        }
        branch(195f, h * .80f, (-PI / 2).toFloat(), minOf(94f, h * .19f), 0)
        if (env.season != WallpaperSeason.WINTER) for (i in 0..85) {
            val angle = rand(i) * 6.28f
            val radius = rand(i + 1) * 119
            val x = 195 + cos(angle) * radius
            val y = h * .43f + sin(angle) * minOf(radius * .64f, h * .19f)
            oval(c, x, y, 2.5f + rand(i + 2) * 4, 4f, withAlpha(colors[3], .79f))
        }
        if (env.season == WallpaperSeason.AUTUMN) for (i in 0..12) {
            val y = h * .42f + (rand(i + 56) * h * .33f + t * (4 + rand(i) * 5)) % (h * .42f)
            val x = 65 + rand(i + 18) * 260 + sin(t * .45f + i) * 12f
            oval(c, x, y, 2.7f, 3.8f, withAlpha(colors[3], .58f))
        }
        if (env.raining || env.snowing) for (i in 0..55) {
            val x = rand(i + 3) * 390
            val y = (rand(i + 4) * h + t * if (env.snowing) 6 else 23) % h
            oval(c, x, y, if (env.snowing) 1.8f else .8f, if (env.snowing) 1.8f else 3.5f, 0x88FFFFFF.toInt())
        }
    }

    private fun rooftops(c: Canvas, h: Float, t: Float, time: Float, signal: Int?) {
        val dark = time < .24f || time > .72f
        sky(c, h, if (dark) 0xFF0B0A2E.toInt() else 0xFF7FB0D8.toInt(),
            if (dark) 0xFF3A2A5A.toInt() else 0xFFF3D9B8.toInt())
        val moon = signal ?: 0xFFDCFFF0.toInt()
        glow(c, 289f, h * .20f, 133f, moon, .24f + sin(t * .15f) * .07f)
        ring(c, 289f, h * .20f, 36f, moon, 8f)
        if (dark) for (i in 0..24) {
            val brightness = .16f + rand(i + 4) * .3f + sin(t * .23f + i) * .09f
            oval(c, rand(i + 13) * 390, rand(i + 77) * h * .51f, 1f, 1f, withAlpha(0xFFE5EAFE.toInt(), brightness))
        } else {
            glow(c, 72f + sin(t * .12f) * 25f, h * .30f, 110f, 0xFFE2F2FA.toInt(), .40f)
        }
        path.rewind()
        path.moveTo(0f, h)
        roof.forEach { (x, y) -> path.lineTo(x, y * h) }
        path.lineTo(390f, h)
        path.close()
        paintPath(c, 0xFF0B091D.toInt())
        if (dark) for (i in 0..67) {
            val x = rand(i) * 390; val y = h * (.69f + rand(i + 1) * .28f)
            fill.color = if (rand(i + 2) > .3f) 0xCCFFD68C.toInt() else 0xAB0CF2BA.toInt()
            c.drawRect(x, y, x + 3.5f, y + 5, fill)
        }
    }

    private fun deepSea(c: Canvas, h: Float, t: Float, accent: Int) {
        sky(c, h, 0xFF0E5A78.toInt(), 0xFF020C1C.toInt())
        for (i in 0..5) {
            val x = 22f + i * 66f + sin(t * .18f + i) * 13
            path.rewind()
            path.moveTo(x, 0f); path.lineTo(x + 38, 0f)
            path.lineTo(x + 110, h * .72f); path.lineTo(x + 32, h * .72f)
            path.close()
            paintPath(c, 0x11B4F0FF)
        }
        for (i in 0..60) {
            val y = (rand(i + 4) * h + t * (2 + rand(i + 45) * 5)) % h
            val r = .7f + rand(i + 5) * 1.6f
            oval(c, rand(i + 3) * 390, y, r, r, withAlpha(0xFFC8FFF0.toInt(), .14f + rand(i) * .38f))
        }
        val center = h * .42f
        glow(c, 195f, center, 116f, accent, .31f)
        ring(c, 195f, center, 55f, accent, 9f)
        for (k in 0..5) {
            val x = 166f + k * 12f
            path.rewind(); path.moveTo(x, center + 49f)
            for (y in 0..108 step 7) path.lineTo(x + sin(y * .056f + k + t * .35f) * 10, center + 49 + y)
            trace(c, withAlpha(accent, .40f), 1.5f)
        }
    }

    private fun paperCut(c: Canvas, h: Float, signal: Int?) {
        c.drawColor(0xFFF7EFE2.toInt())
        ring(c, 110f, h * .20f, 38f, signal ?: 0xFF24412F.toInt(), 8.5f)
        val layers = intArrayOf(0xFFEFD9C3.toInt(), 0xFFE6C3A8.toInt(), 0xFFCFA58A.toInt(),
            0xFFA97A66.toInt(), 0xFF6B4A4A.toInt())
        layers.forEachIndexed { layer, color ->
            val base = h * (.49f + layer * .102f)
            hills(h, base, layer, minOf(h * .075f, 27f), .014f)
            fill.setShadowLayer(7f, 0f, -3f, 0x443C1E14)
            paintPath(c, color)
            fill.clearShadowLayer()
        }
    }

    private fun photo(c: Canvas, h: Float, bitmap: Bitmap?, signal: Int?, position: WallpaperRing) {
        if (bitmap == null) {
            sky(c, h, 0xFFF6C58F.toInt(), 0xFF3A1F2A.toInt())
            glow(c, 110f, h * .27f, 112f, 0xFFFFE8C2.toInt(), .75f)
            glow(c, 270f, h * .65f, 132f, 0xFF8A3A3A.toInt(), .68f)
            glow(c, 91f, h * .73f, 119f, 0xFF3D6F8A.toInt(), .72f)
        } else {
            val scale = maxOf(390f / bitmap.width, h / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            frame.set((390f - width) / 2, (h - height) / 2, (390f + width) / 2, (h + height) / 2)
            fill.color = Color.WHITE
            c.drawBitmap(bitmap, null, frame, fill)
        }
        c.drawColor(0x29060330)
        ring(c, 195f, h * position.fraction, minOf(74f, h * .22f), signal ?: 0xDAFFFFFF.toInt(), 10f)
    }

    private fun meadow(c: Canvas, h: Float, t: Float, signal: Int?, wind: Float) {
        sky(c, h, 0xFFFDE2C4.toInt(), 0xFF7A9A5A.toInt())
        glow(c, 195f, h * .43f, 183f, 0xFFFFF0D2.toInt(), .78f)
        ring(c, 195f, h * .44f, 42f, signal ?: 0xD624412F.toInt(), 8f)
        for (i in 0..148) {
            val x = rand(i) * 390
            val base = h * (.63f + rand(i + 1) * .44f)
            val length = minOf(h * .36f, 37f + rand(i + 2) * 91)
            val bend = 12 + rand(i + 3) * 15 + sin(t * (.27f + wind * .70f) + i * .19f) * (2 + wind * 17)
            path.rewind()
            path.moveTo(x, base)
            path.quadTo(x + bend * .4f, base - length * .63f, x + bend, base - length)
            trace(c, Color.argb(205, (49 + rand(i) * 40).toInt(), (89 + rand(i + 1) * 51).toInt(), 40),
                1.2f + rand(i + 4) * 1.5f)
        }
    }

    private fun inkWash(c: Canvas, h: Float, time: Float, signal: Int?) {
        c.drawColor(if (time < .20f || time > .83f) 0xFFEAE7E0.toInt() else 0xFFF4F1EA.toInt())
        ring(c, 270f, h * .18f, 28f, signal ?: 0xFFB89150.toInt(), 6.5f)
        for (layer in 0..3) {
            val base = h * (.44f + layer * .105f)
            hills(h, base, layer * 2, minOf(h * .14f, 51f), .016f)
            fill.setShadowLayer(maxOf(1, 5 - layer).toFloat(), 0f, -2f, 0x30212C35)
            paintPath(c, withAlpha(0xFF26313A.toInt(), .13f + layer * .12f))
            fill.clearShadowLayer()
        }
        segment(c, 140f, h * .30f, 150f, h * .305f, 0x98303B44.toInt(), 1.7f)
        segment(c, 150f, h * .305f, 160f, h * .30f, 0x98303B44.toInt(), 1.7f)
    }

    private fun hills(h: Float, base: Float, layer: Int, amp: Float, frequency: Float) {
        path.rewind()
        path.moveTo(0f, h)
        for (x in 0..390 step 6) {
            val wave = sin(x * frequency + layer * 1.31f) * amp
            val detail = sin(x * .037f - layer) * amp * .21f
            path.lineTo(x.toFloat(), base + wave + detail)
        }
        path.lineTo(390f, h)
        path.close()
    }

    private fun sky(c: Canvas, h: Float, top: Int, bottom: Int) {
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 390f, h, fill)
        fill.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        fill.color = Color.WHITE
        fill.shader = RadialGradient(x, y, radius, withAlpha(color, alpha), withAlpha(color, 0f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, fill)
        fill.shader = null
    }

    private fun ring(c: Canvas, x: Float, y: Float, radius: Float, color: Int, width: Float) {
        frame.set(x - radius, y - radius, x + radius, y + radius)
        line.color = color
        line.strokeWidth = width
        c.drawArc(frame, -22.5f, 315f, false, line)
    }

    private fun oval(c: Canvas, x: Float, y: Float, width: Float, height: Float, color: Int) {
        fill.color = color
        c.drawOval(x - width, y - height, x + width, y + height, fill)
    }

    private fun segment(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        line.color = color
        line.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, line)
    }

    private fun trace(c: Canvas, color: Int, width: Float) {
        line.color = color
        line.strokeWidth = width
        c.drawPath(path, line)
    }

    private fun paintPath(c: Canvas, color: Int) {
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun rand(seed: Int): Float {
        val value = sin(seed * 999.0) * 10_000
        return (value - floor(value)).toFloat()
    }

    private companion object {
        val roof = listOf(0f to .68f, 30f to .66f, 30f to .60f, 60f to .60f, 62f to .48f, 66f to .40f,
            70f to .48f, 72f to .60f, 110f to .60f, 110f to .64f, 150f to .64f, 152f to .50f, 158f to .32f,
            164f to .50f, 166f to .58f, 200f to .58f, 200f to .62f, 240f to .62f, 242f to .55f, 262f to .55f,
            264f to .45f, 268f to .38f, 272f to .45f, 274f to .56f, 310f to .56f, 310f to .63f, 350f to .63f,
            352f to .59f, 390f to .59f)
    }
}
