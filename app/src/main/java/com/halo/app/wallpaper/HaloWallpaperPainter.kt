package com.halo.app.wallpaper

import android.graphics.*
import com.halo.app.wallpaper.v13.SkyPhase
import com.halo.app.wallpaper.v13.SkyState
import com.halo.app.wallpaper.v13.SkyWeather
import kotlin.math.*

internal enum class WallpaperCollection { LIVELY, CALM, WORLD }

internal enum class HaloWallpaper(val collection: WallpaperCollection, val nearStill: Boolean = false) {
    DRIFT(WallpaperCollection.LIVELY), AURORA(WallpaperCollection.LIVELY),
    TIDE(WallpaperCollection.WORLD), DAY_ARC(WallpaperCollection.WORLD),
    CONSTELLATION(WallpaperCollection.LIVELY), PAPER(WallpaperCollection.CALM, true),
    NORTHERN_FOREST(WallpaperCollection.LIVELY), BALTIC_DUNES(WallpaperCollection.CALM),
    RAIN_GLASS(WallpaperCollection.WORLD), LUMEN_GARDEN(WallpaperCollection.WORLD),
    NIGHT_TRAIN(WallpaperCollection.LIVELY), SEASONS(WallpaperCollection.WORLD),
    RIGA_ROOFTOPS(WallpaperCollection.WORLD), DEEP_SEA(WallpaperCollection.LIVELY),
    PAPER_CUT(WallpaperCollection.CALM, true), YOUR_PHOTO(WallpaperCollection.CALM, true),
    MEADOW_WIND(WallpaperCollection.WORLD), INK_WASH(WallpaperCollection.CALM, true);

    val usesEnvironment: Boolean
        get() = this in setOf(RAIN_GLASS, LUMEN_GARDEN, SEASONS, MEADOW_WIND, BALTIC_DUNES)
}
internal enum class HaloAmbient { IDLE, LISTENING, WORKING, NEEDS_YOU }

/**
 * Painters. Coordinates are in a 390-wide reference space (same as the boards/film source alive.js → paintWP);
 * canvas is scaled once per frame. Blur = BlurMaskFilter on soft shapes (hardware canvas supports it on API 28+;
 * on older devices fall back to larger radial gradients without blur).
 */
internal class HaloWallpaperPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val toolkit = ToolkitWallpaperScenes()

    fun paint(c: Canvas, w: HaloWallpaper, t: Float, tod: Float, accent: Int, st: HaloAmbient,
        amt: Float, ripples: Boolean, dim: Float, width: Float = c.width.toFloat(), height: Float = c.height.toFloat(),
        environment: WallpaperEnvironment = WallpaperEnvironment(), photo: Bitmap? = null,
        ring: WallpaperRing = WallpaperRing.CENTER,
        ambientTime: Float = t,
        sky: SkyState? = null,
    ) {
        if (width <= 0f || height <= 0f) return
        val scale = width / 390f
        val savePoint = c.save()
        c.clipRect(0f, 0f, width, height)
        c.scale(scale, scale)
        val h = height / scale
        when (w) {
            HaloWallpaper.DRIFT -> drift(c, h, t, accent)
            HaloWallpaper.AURORA -> aurora(c, h, t, accent)
            HaloWallpaper.TIDE -> tide(c, h, t, tod, accent)
            HaloWallpaper.DAY_ARC -> if (sky == null) dayArc(c, h, t, tod, accent) else followSky(c, h, t, sky)
            HaloWallpaper.CONSTELLATION -> constellation(c, h, t, SIGNAL_MINT, st == HaloAmbient.WORKING)
            HaloWallpaper.PAPER -> paper(c, h, t)
            else -> toolkit.paint(c, w, h, t, tod, accent, st, environment, photo, ring)
        }
        when (st) {
            HaloAmbient.LISTENING -> {
                val y = h * .83f
                glow(c, 195f, h, 340f, SIGNAL_MINT, .28f * amt)
                if (ripples) for (wave in 0..2) {
                    val radius = (ambientTime * 88f - wave * 94f).takeIf { it > 0f }?.rem(390f) ?: continue
                    ring(c, 195f, y, radius, SIGNAL_MINT, (1f - radius / 390f) * .53f * amt, 2.5f)
                }
            }
            HaloAmbient.NEEDS_YOU -> glow(c, 195f, h + 40, 460f, 0xFFFF9F6B.toInt(), .42f * amt * (.85f + .15f * sin(ambientTime * .75f)))
            HaloAmbient.WORKING -> if (w != HaloWallpaper.CONSTELLATION) glow(c, 195f, h * .75f, 260f, SIGNAL_MINT, .19f * amt)
            HaloAmbient.IDLE -> Unit
        }
        if (dim > 0) c.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 10))
        c.restoreToCount(savePoint)
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Float) {
        p.color = Color.WHITE
        p.shader = RadialGradient(x, y, r, withA(col, a), withA(col, 0f), Shader.TileMode.CLAMP); c.drawCircle(x, y, r, p); p.shader = null
    }
    private fun ring(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Float, wdt: Float) { stroke.color = withA(col, a); stroke.strokeWidth = wdt; c.drawCircle(x, y, r, stroke) }
    private fun withA(col: Int, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(col), Color.green(col), Color.blue(col))
    private fun rnd(i: Int): Float { val x = sin(i * 127.1 + 311.7) * 43758.5453; return (x - floor(x)).toFloat() }

    /** Open halo ring, gap at 45° (top-right), as in the brand mark. */
    private fun haloRing(c: Canvas, r: Float, col: Int, wdt: Float) {
        stroke.color = col; stroke.strokeWidth = wdt
        c.drawArc(RectF(-r, -r, r, r), -22.5f, 315f, false, stroke) // gap centred at -45° (between 1 and 2 o'clock), like the mark
    }

    private fun drift(c: Canvas, h: Float, t: Float, acc: Int) {
        c.drawColor(0xFF060330.toInt())
        listOf(acc to .30f, 0xFFC9B8FF.toInt() to .26f, 0xFFF3C0D9.toInt() to .16f, 0xFFB6DFE2.toInt() to .18f).forEachIndexed { i, (col, a) ->
            glow(c, 195 + 130 * cos(t * .11f * (1 + i * .3f) + i * 1.7f), h * .4f + 190 * sin(t * .08f * (1 + i * .2f) + i), 230f, col, a)
        }
        c.save(); c.translate(195f, h * .42f); val br = 1 + .035f * sin(t * 1.1f); c.scale(br, br)
        stroke.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL); haloRing(c, 150f, withA(acc, .42f), 34f); stroke.maskFilter = null
        haloRing(c, 150f, withA(Color.WHITE, .18f), 2f); c.restore()
    }

    private fun aurora(c: Canvas, h: Float, t: Float, acc: Int) {
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF04021F.toInt(), 0xFF0D0840.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val bands = listOf(acc to h * .23f, 0xFFC9B8FF.toInt() to h * .35f,
            0xFFB6DFE2.toInt() to h * .46f, 0xFFF3C0D9.toInt() to h * .57f)
        p.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL); p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        bands.forEachIndexed { b, (col, base) ->
            val path = Path(); val top = ArrayList<PointF>()
            var x = -20f; while (x <= 410f) { top += PointF(x, base + 38 * sin(x * .012f + t * .35f * (1 + b * .25f) + b * 1.3f) + 18 * sin(x * .031f - t * .5f + b)); x += 10f }
            path.moveTo(top[0].x, top[0].y); top.forEach { path.lineTo(it.x, it.y) }
            top.asReversed().forEach { path.lineTo(it.x, it.y + 90 + 30 * sin(it.x * .02f + t * .4f + b)) }; path.close()
            p.shader = LinearGradient(0f, base - 40, 0f, base + 140, intArrayOf(withA(col, 0f), withA(col, .34f - b * .05f), withA(col, 0f)), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null; p.maskFilter = null; p.xfermode = null
        for (i in 0 until 40) { p.color = withA(Color.WHITE, (.25f + .35f * (.5f + .5f * sin(t * 1.3f + i))) * .6f); c.drawCircle(rnd(i) * 390, rnd(i + 50) * h * .55f, rnd(i + 9) * 1.2f + .3f, p) }
    }

    private fun sky(tod: Float): Pair<Int, Int> {
        val keys = listOf(Triple(0f, 0xFF04021F, 0xFF120D4A), Triple(.25f, 0xFF1A1760, 0xFFE4B19C), Triple(.5f, 0xFF1D3B8A, 0xFFB6DFE2), Triple(.75f, 0xFF2A1252, 0xFFF3C0D9), Triple(1f, 0xFF04021F, 0xFF120D4A))
        for (i in 0 until keys.size - 1) { val (a, t1, b1) = keys[i]; val (b, t2, b2) = keys[i + 1]
            if (tod in a..b) { val k = (tod - a) / (b - a); val e = k * k * (3 - 2 * k); return mix(t1.toInt(), t2.toInt(), e) to mix(b1.toInt(), b2.toInt(), e) } }
        return 0xFF04021F.toInt() to 0xFF120D4A.toInt()
    }
    private fun mix(a: Int, b: Int, k: Float) = Color.rgb((Color.red(a) * (1 - k) + Color.red(b) * k).toInt(), (Color.green(a) * (1 - k) + Color.green(b) * k).toInt(), (Color.blue(a) * (1 - k) + Color.blue(b) * k).toInt())

    private val skyTops = intArrayOf(0xff060330.toInt(), 0xff332369.toInt(), 0xff5579bc.toInt(),
        0xff346ab3.toInt(), 0xff574184.toInt(), 0xff241452.toInt())
    private val skyHorizons = intArrayOf(0xff18114f.toInt(), 0xffffbfaa.toInt(), 0xfff7dfc6.toInt(),
        0xffcbe9e3.toInt(), 0xffffad79.toInt(), 0xffb77ca0.toInt())
    private val seasons = intArrayOf(0xffcff0ff.toInt(), 0xffd5ebcd.toInt(), 0xffffedbd.toInt(), 0xffdba186.toInt())
    private val skyLightsX = floatArrayOf(.82f, .34f, .64f, .79f, .68f, .45f)
    private val skyLightsY = floatArrayOf(.22f, .61f, .43f, .22f, .53f, .69f)
    private val skyRidge = Path()
    private var skyGradientKey: Triple<Int, Int, Float>? = null
    private var skyGradient: LinearGradient? = null

    private fun followSky(c: Canvas, h: Float, t: Float, sky: SkyState) {
        val fade = sky.blend * sky.blend * (3 - 2 * sky.blend)
        val top = mix(skyTops[sky.phase.ordinal], skyTops[sky.next.ordinal], fade)
        val horizon = mix(mix(skyHorizons[sky.phase.ordinal], skyHorizons[sky.next.ordinal], fade),
            seasons[sky.season.ordinal], .12f)
        val atmosphere = when (sky.weather) {
            SkyWeather.CLEAR -> 0f
            SkyWeather.CLOUDS -> .16f
            SkyWeather.RAIN, SkyWeather.SNOW -> .24f
            SkyWeather.FOG -> .36f
        }
        val key = Triple(mix(top, 0xffb0baca.toInt(), atmosphere), horizon, h)
        if (skyGradientKey != key) {
            skyGradientKey = key
            skyGradient = LinearGradient(0f, 0f, 0f, h, key.first, horizon, Shader.TileMode.CLAMP)
        }
        p.color = Color.WHITE
        p.shader = skyGradient
        c.drawRect(0f, 0f, 390f, h, p)
        p.shader = null
        val stars = if (sky.phase == SkyPhase.NIGHT) 1 - fade else if (sky.next == SkyPhase.NIGHT) fade else 0f
        if (stars > 0f) repeat(35) { index ->
            p.color = withA(Color.WHITE, stars * (1 - atmosphere) * (.14f + rnd(index) * .36f))
            c.drawCircle(rnd(index + 40) * 390, rnd(index + 75) * h * .62f, .5f + rnd(index + 2), p)
        }
        val phase = sky.phase.ordinal
        val next = sky.next.ordinal
        val discX = 390 * (skyLightsX[phase] * (1 - fade) + skyLightsX[next] * fade)
        val discY = h * (skyLightsY[phase] * (1 - fade) + skyLightsY[next] * fade)
        val disc = mix(0xffffdf87.toInt(), 0xfff2edff.toInt(), stars)
        glow(c, discX, discY, 75f, disc, .34f * (1 - atmosphere))
        p.color = withA(disc, .92f * (1 - atmosphere))
        c.drawCircle(discX, discY, 14 + 4 * stars, p)
        p.color = mix(top, 0xff18234b.toInt(), .68f)
        skyRidge.rewind()
        skyRidge.moveTo(0f, h * .83f)
        skyRidge.cubicTo(130f, h * .75f, 256f, h * .71f, 390f, h * .79f)
        skyRidge.lineTo(390f, h)
        skyRidge.lineTo(0f, h)
        skyRidge.close()
        c.drawPath(skyRidge, p)
        p.color = mix(top, 0xff08062f.toInt(), .82f)
        skyRidge.rewind()
        skyRidge.moveTo(0f, h * .84f)
        skyRidge.cubicTo(126f, h * .77f, 242f, h * .89f, 390f, h * .94f)
        skyRidge.lineTo(390f, h)
        skyRidge.lineTo(0f, h)
        skyRidge.close()
        c.drawPath(skyRidge, p)
        if (atmosphere > 0f) repeat(3) { index ->
            glow(c, 195 + sin(t * .15f + index) * 100, h * (.40f + index * .14f), 175f, Color.WHITE, atmosphere * .55f)
        }
        if (sky.weather == SkyWeather.RAIN || sky.weather == SkyWeather.SNOW) repeat(28) { index ->
            val y = (rnd(index + 21) * h + t * (if (sky.weather == SkyWeather.RAIN) 28 else 7)) % h
            val x = rnd(index + 90) * 390
            p.color = withA(Color.WHITE, .20f)
            if (sky.weather == SkyWeather.RAIN) c.drawLine(x, y, x - 2, y + 9, p)
            else c.drawCircle(x, y, 1 + rnd(index), p)
        }
    }

    private fun tide(c: Canvas, h: Float, t: Float, tod: Float, acc: Int) {
        val (a, b) = sky(tod); p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(a, b, b), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val sy = h * (.62f - .38f * sin(PI.toFloat() * ((tod - .2f) / .6f).coerceIn(0f, 1f))); glow(c, 270f, sy, 140f, 0xFFFFE7C9.toInt(), .75f)
        val dark = tod < .18f || tod > .86f
        for (L in 0..4) { val base = h * (.60f + L * .075f); val amp = 10f + L * 4; val sp = .5f + L * .18f; val path = Path(); path.moveTo(0f, h)
            var x = 0f; while (x <= 390f) { path.lineTo(x, base + amp * sin(x * (.018f - L * .002f) + t * sp + L * 1.7f) + amp * .4f * sin(x * .05f - t * sp * 1.3f)); x += 6f }
            path.lineTo(390f, h); path.close(); p.color = withA(mix(if (dark) 0xFF16125A.toInt() else 0xFF2A2580.toInt(), 0xFF060330.toInt(), L / 4.5f), .86f); c.drawPath(path, p)
            stroke.color = withA(if (L == 0) acc else Color.WHITE, if (L == 0) .5f else .08f); stroke.strokeWidth = 1.5f; c.drawPath(path, stroke) }
    }

    private fun dayArc(c: Canvas, h: Float, t: Float, tod: Float, acc: Int) {
        val (a, b) = sky(tod); p.shader = LinearGradient(0f, 0f, 0f, h, a, b, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val night = tod < .2f || tod > .82f
        if (night) for (i in 0 until 70) { p.color = withA(Color.WHITE, .55f * (.4f + .6f * (.5f + .5f * sin(t * 2 + i * 1.7f)))); c.drawCircle(rnd(i) * 390, rnd(i + 80) * h * .7f, rnd(i + 3) * 1.3f + .3f, p) }
        val cy = h * .62f; val R = 230f; stroke.pathEffect = DashPathEffect(floatArrayOf(2f, 7f), 0f); stroke.color = withA(Color.WHITE, .25f); stroke.strokeWidth = 1.5f
        c.drawArc(RectF(195 - R, cy - R, 195 + R, cy + R), 180f, 180f, false, stroke); stroke.pathEffect = null
        val pr = if (night) ((if (tod > .5f) tod - .82f else tod + .18f) / .38f) else (tod - .2f) / .62f
        val ang = PI + PI * pr.coerceIn(0f, 1f); val sx = 195 + R * cos(ang).toFloat(); val sy = cy + R * sin(ang).toFloat()
        glow(c, sx, sy, 120f, if (night) 0xFFC9B8FF.toInt() else 0xFFFFE7C9.toInt(), .55f)
        c.save(); c.translate(sx, sy); c.rotate(t * 5f); haloRing(c, 26f, if (night) 0xFFC9B8FF.toInt() else acc, 9f); c.restore()
    }

    private fun constellation(c: Canvas, h: Float, t: Float, acc: Int, working: Boolean) {
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF050228.toInt(), 0xFF120D4A.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val pts = List(48) { i -> PointF(rnd(i) * 390 + 18 * sin(t * .25f + i), rnd(i + 99) * h + 14 * cos(t * .2f + i * 1.3f)) }
        stroke.strokeWidth = 1.2f
        for (i in pts.indices) for (j in i + 1 until pts.size) { val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y); if (d < 95) {
            val a = if (working) (1 - d / 95) * (.35f + .55f * (.5f + .5f * sin(t * 3 - pts[i].y / h * 8))) else (1 - d / 95) * .32f
            stroke.color = withA(if (working) acc else Color.WHITE, a); c.drawLine(pts[i].x, pts[i].y, pts[j].x, pts[j].y, stroke) } }
        pts.forEachIndexed { i, pt -> p.color = withA(if (working && i % 3 == 0) acc else Color.WHITE, .8f); c.drawCircle(pt.x, pt.y, 1.6f + rnd(i + 7) * 1.4f, p) }
    }

    private fun paper(c: Canvas, h: Float, t: Float) {
        c.drawColor(0xFFF4F1E8.toInt())
        listOf(0xFF8C9A7B.toInt() to .30f, 0xFFB6DFE2.toInt() to .38f, 0xFFF3C0D9.toInt() to .28f).forEachIndexed { i, (col, a) ->
            glow(c, 195 + 120 * cos(t * .06f + i * 2), h * .45f + 200 * sin(t * .05f + i * 2.2f), 220f, col, a) }
        c.save(); c.translate(195f, h * .4f); c.rotate(t * 1.7f); haloRing(c, 150f, withA(0xFF0A7F66.toInt(), .16f), 28f); c.restore()
    }

    private companion object { val SIGNAL_MINT = 0xFF0CF2BA.toInt() }
}
