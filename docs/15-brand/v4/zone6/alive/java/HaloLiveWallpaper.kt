package com.halo.app.wallpaper

/*
 * halo live wallpapers (pack 7.2). Reference implementation — not compiled against 98aa2fb.
 * Draws only while visible, ≤30 fps, Still under battery saver / Remove animations / HaloMotion.STILL.
 * The same painters back the in-app background (HaloBackgroundStyle gains LIVE + HaloWallpaper).
 */

import android.content.Context
import android.graphics.*
import android.os.PowerManager
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.halo.app.ui.theme.HaloMotion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import kotlin.math.*

enum class HaloWallpaper { DRIFT, AURORA, TIDE, DAY_ARC, CONSTELLATION, PAPER }
enum class HaloAmbient { IDLE, LISTENING, WORKING, NEEDS_YOU }

/** Process-wide halo state for ambient surfaces (wallpaper, widgets). Written by the existing runtime owners. */
object HaloAmbientState {
    private val _state = MutableStateFlow(HaloAmbient.IDLE)
    val state: StateFlow<HaloAmbient> = _state
    fun set(s: HaloAmbient) { _state.value = s }
}

data class WallpaperPrefs(
    val wallpaper: HaloWallpaper = HaloWallpaper.DRIFT,
    val accent: Int = 0xFF0CF2BA.toInt(),
    val reactsToHalo: Boolean = true,
    val calmerAtNight: Boolean = true,
    val motion: HaloMotion = HaloMotion.GENTLE,
)

class HaloLiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = HaloEngine()

    inner class HaloEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val painter = HaloWallpaperPainter()
        private var visible = false
        private var start = System.nanoTime()
        private var lastFrame = 0L
        private var ambient = HaloAmbient.IDLE
        private var ambientSince = 0L
        private var prefs = WallpaperPrefs()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = WallpaperPrefsStore.read(this@HaloLiveWallpaperService)
            scope.launch { HaloAmbientState.state.collect { ambient = it; ambientSince = System.nanoTime(); if (still()) drawOnce() } }
            scope.launch { WallpaperPrefsStore.changes(this@HaloLiveWallpaperService).collect { prefs = it; drawOnce() } }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v && !still()) Choreographer.getInstance().postFrameCallback(this)
            else Choreographer.getInstance().removeFrameCallback(this)
            if (v) drawOnce()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; Choreographer.getInstance().removeFrameCallback(this) }
        override fun onDestroy() { scope.cancel(); Choreographer.getInstance().removeFrameCallback(this); super.onDestroy() }

        /** Still when the user, the system or the battery asks for it. */
        private fun still(): Boolean {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val anim = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            return prefs.motion == HaloMotion.STILL || pm.isPowerSaveMode || anim == 0f
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible || still()) return
            if (frameTimeNanos - lastFrame >= 33_000_000L) { lastFrame = frameTimeNanos; draw(frameTimeNanos) }
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun drawOnce() = draw(System.nanoTime())

        private fun draw(now: Long) {
            val holder = surfaceHolder
            val canvas = holder.lockHardwareCanvas() ?: return
            try {
                val night = prefs.calmerAtNight && (isPreview.not() && hour() !in 7..21)
                val speed = when (prefs.motion) { HaloMotion.FULL -> 1f; HaloMotion.GENTLE -> .5f; HaloMotion.STILL -> 0f } * (if (night) .5f else 1f)
                val t = (now - start) / 1e9f * speed
                val st = if (prefs.reactsToHalo) ambient else HaloAmbient.IDLE
                val amt = min(1f, (now - ambientSince) / 6e8f)
                painter.paint(canvas, prefs.wallpaper, t, timeOfDay(), prefs.accent, st, amt,
                    ripples = prefs.motion == HaloMotion.FULL, dim = if (night) .25f else 0f)
            } finally { holder.unlockCanvasAndPost(canvas) }
        }
    }
}

private fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
/** 0 = midnight, .25 = 06:00, .5 = noon, .75 = 18:00. */
fun timeOfDay(): Float { val c = Calendar.getInstance(); return (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) / 1440f }

/**
 * Painters. Coordinates are in a 390-wide reference space (same as the boards/film source alive.js → paintWP);
 * canvas is scaled once per frame. Blur = BlurMaskFilter on soft shapes (hardware canvas supports it on API 28+;
 * on older devices fall back to larger radial gradients without blur).
 */
class HaloWallpaperPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun paint(c: Canvas, w: HaloWallpaper, t: Float, tod: Float, accent: Int, st: HaloAmbient, amt: Float, ripples: Boolean, dim: Float) {
        val s = c.width / 390f; c.save(); c.scale(s, s)
        val h = c.height / s
        when (w) {
            HaloWallpaper.DRIFT -> drift(c, h, t, accent)
            HaloWallpaper.AURORA -> aurora(c, h, t, accent)
            HaloWallpaper.TIDE -> tide(c, h, t, tod, accent)
            HaloWallpaper.DAY_ARC -> dayArc(c, h, t, tod, accent)
            HaloWallpaper.CONSTELLATION -> constellation(c, h, t, accent, st == HaloAmbient.WORKING)
            HaloWallpaper.PAPER -> paper(c, h, t)
        }
        when (st) {
            HaloAmbient.LISTENING -> { glow(c, 195f, h, 340f, accent, .28f * amt); if (ripples) for (k in 0..3) { val r = (t * 90 + k * 95) % 380; ring(c, 195f, h - 150, r, accent, (1 - r / 380) * .55f * amt, 2.5f) } }
            HaloAmbient.NEEDS_YOU -> glow(c, 195f, h + 40, 460f, 0xFFFF9F6B.toInt(), .42f * amt * (.75f + .25f * sin(t * 2)))
            HaloAmbient.WORKING -> if (w != HaloWallpaper.CONSTELLATION) glow(c, 195f, h - 140, 260f, accent, .16f * amt)
            HaloAmbient.IDLE -> Unit
        }
        if (dim > 0) c.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 10))
        c.restore()
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Float) {
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
        listOf(acc to .30f, 0xFFC9B8FF.toInt() to .26f, 0xFFFF9F6B.toInt() to .16f, 0xFFB6DFE2.toInt() to .18f).forEachIndexed { i, (col, a) ->
            glow(c, 195 + 130 * cos(t * .11f * (1 + i * .3f) + i * 1.7f), h * .4f + 190 * sin(t * .08f * (1 + i * .2f) + i), 230f, col, a)
        }
        c.save(); c.translate(195f, h * .42f); c.rotate(Math.toDegrees((t * .06).toDouble()).toFloat()); val br = 1 + .035f * sin(t * 1.1f); c.scale(br, br)
        stroke.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL); haloRing(c, 150f, withA(acc, .42f), 34f); stroke.maskFilter = null
        haloRing(c, 150f, withA(Color.WHITE, .18f), 2f); c.restore()
    }

    private fun aurora(c: Canvas, h: Float, t: Float, acc: Int) {
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF04021F.toInt(), 0xFF0D0840.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val bands = listOf(acc to 150f, 0xFFC9B8FF.toInt() to 260f, 0xFFB6DFE2.toInt() to 370f, 0xFFF3C0D9.toInt() to 470f)
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
        val keys = listOf(Triple(0f, 0xFF04021F, 0xFF120D4A), Triple(.25f, 0xFF1A1760, 0xFFFF9F6B), Triple(.5f, 0xFF1D3B8A, 0xFFB6DFE2), Triple(.75f, 0xFF2A1252, 0xFFF3C0D9), Triple(1f, 0xFF04021F, 0xFF120D4A))
        for (i in 0 until keys.size - 1) { val (a, t1, b1) = keys[i]; val (b, t2, b2) = keys[i + 1]
            if (tod in a..b) { val k = (tod - a) / (b - a); val e = k * k * (3 - 2 * k); return mix(t1.toInt(), t2.toInt(), e) to mix(b1.toInt(), b2.toInt(), e) } }
        return 0xFF04021F.toInt() to 0xFF120D4A.toInt()
    }
    private fun mix(a: Int, b: Int, k: Float) = Color.rgb((Color.red(a) * (1 - k) + Color.red(b) * k).toInt(), (Color.green(a) * (1 - k) + Color.green(b) * k).toInt(), (Color.blue(a) * (1 - k) + Color.blue(b) * k).toInt())

    private fun tide(c: Canvas, h: Float, t: Float, tod: Float, acc: Int) {
        val (a, b) = sky(tod); p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(a, b, b), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, 390f, h, p); p.shader = null
        val sy = h * (.62f - .38f * sin(PI.toFloat() * ((tod - .2f) / .6f).coerceIn(0f, 1f))); glow(c, 270f, sy, 140f, 0xFFFFE7C9.toInt(), .75f)
        val dark = tod < .18f || tod > .86f
        for (L in 0..4) { val base = h * .6f + L * 62; val amp = 10f + L * 4; val sp = .5f + L * .18f; val path = Path(); path.moveTo(0f, h)
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
        c.save(); c.translate(sx, sy); c.rotate(t * 17f); haloRing(c, 26f, if (night) 0xFFC9B8FF.toInt() else acc, 9f); c.restore()
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
}

/** Minimal prefs store — reuse the "halo_look" SharedPreferences from 6.4. */
object WallpaperPrefsStore {
    fun read(ctx: Context): WallpaperPrefs { val sp = ctx.getSharedPreferences("halo_look", Context.MODE_PRIVATE)
        return WallpaperPrefs(
            wallpaper = runCatching { HaloWallpaper.valueOf(sp.getString("wallpaper", "DRIFT")!!) }.getOrDefault(HaloWallpaper.DRIFT),
            accent = sp.getInt("wallpaper_accent", 0xFF0CF2BA.toInt()),
            reactsToHalo = sp.getBoolean("wallpaper_reacts", true),
            calmerAtNight = sp.getBoolean("wallpaper_night", true),
            motion = runCatching { HaloMotion.valueOf(sp.getString("motion", "GENTLE")!!) }.getOrDefault(HaloMotion.GENTLE)) }
    fun changes(ctx: Context) = kotlinx.coroutines.flow.callbackFlow {
        val sp = ctx.getSharedPreferences("halo_look", Context.MODE_PRIVATE)
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read(ctx)) }
        sp.registerOnSharedPreferenceChangeListener(l); awaitClose { sp.unregisterOnSharedPreferenceChangeListener(l) } }
}
