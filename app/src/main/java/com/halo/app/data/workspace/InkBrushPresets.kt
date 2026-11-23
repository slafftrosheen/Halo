package com.halo.app.data.workspace

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.halo.sdk.runtime.ink.InkBrushBlend
import com.halo.sdk.runtime.ink.InkBrushStyle
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkStroke
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

internal data class InkBrushPreset(
    val id: String,
    val version: Int,
    val name: String,
    val family: String,
    val engine: String,
    val tip: String,
    val angle: Float,
    val hardness: Float,
    val spacing: Float,
    val scatter: Float,
    val grain: Boolean,
    val wetness: Float,
    val sizeCurve: List<Pair<Float, Float>>,
    val opacityCurve: List<Pair<Float, Float>>,
    val speedSize: Float,
    val opacity: Int,
    val blend: InkBrushBlend,
) {
    fun style(color: Int, size: Int, flow: Int) = InkBrushStyle(id, version, color, size, opacity, flow, blend)
}

internal object InkBrushPresets {
    @Volatile private var manager: AssetManager? = null

    fun initialize(assets: AssetManager) { manager = assets }

    private val catalogue by lazy {
        val data = manager?.open("brushes/brushes.json")?.bufferedReader()?.use { JSONObject(it.readText()) }
            ?.getJSONArray("brushes") ?: JSONArray()
        List(data.length()) { index ->
            val item = data.getJSONObject(index)
            val tip = item.getJSONObject("tip")
            val pen = item.optJSONObject("pen")
            fun curve(name: String) = pen?.optJSONArray(name)?.let { values ->
                List(values.length()) { point ->
                    val pair = values.getJSONArray(point)
                    pair.getDouble(0).toFloat() to pair.getDouble(1).toFloat()
                }
            }.orEmpty()
            InkBrushPreset(item.getString("id"), item.getInt("v"), item.getString("name"), item.getString("set"),
                item.getString("engine"), tip.optString("shape"), tip.optDouble("angle", 0.0).toFloat(),
                tip.optDouble("hardness", 1.0).toFloat(), item.optDouble("spacing", .15).toFloat(),
                item.optDouble("scatter", 0.0).toFloat(), item.has("grain"),
                item.optJSONObject("wet")?.optDouble("wetness", 0.0)?.toFloat() ?: 0f,
                curve("pressure.size"), curve("pressure.opacity"), pen?.optDouble("speed.size", 0.0)?.toFloat() ?: 0f,
                (item.optDouble("opacity", 1.0) * 1000).toInt().coerceIn(0, 1000),
                InkBrushBlend.entries.firstOrNull { it.name.equals(item.optString("blend"), ignoreCase = true) }
                    ?: InkBrushBlend.NORMAL)
        }
    }

    // Erasing and smudging already use the respective document owners.
    fun drawingBrushes(): List<InkBrushPreset> = catalogue.filterNot { it.engine in setOf("eraser", "smudge") }
    fun find(style: InkBrushStyle): InkBrushPreset? = catalogue.firstOrNull { it.id == style.id && it.version == style.version }
}

/** The same seeded stroke is drawn in the editor, thumbnails, and exported bitmaps. */
internal fun drawStyledInk(canvas: Canvas, stroke: InkStroke, rect: RectF, alpha: Float = 1f) {
    val style = stroke.brush ?: return
    val preset = InkBrushPresets.find(style)
    val width = (rect.width() * style.size / 1_000_000f).coerceAtLeast(.75f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.argb
        xfermode = when (style.blend) {
            InkBrushBlend.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            InkBrushBlend.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            else -> null
        }
    }
    val baseAlpha = paint.alpha * style.opacity / 1000f * style.flow / 1000f * alpha
    if (baseAlpha <= 0f) return
    fun curve(points: List<Pair<Float, Float>>, value: Float): Float {
        if (points.isEmpty()) return 1f
        val next = points.indexOfFirst { it.first >= value }
        if (next <= 0) return if (next == 0) points.first().second else points.last().second
        val (x, y) = points[next - 1]
        val (endX, endY) = points[next]
        return y + (endY - y) * (value - x) / (endX - x).coerceAtLeast(.001f)
    }
    val random = Random(style.seed)
    val step = (width * (preset?.spacing ?: .15f)).coerceAtLeast(rect.width() / 900f).coerceAtLeast(.75f)
    fun x(point: InkPoint) = rect.left + rect.width() * point.x / 1000f
    fun y(point: InkPoint) = rect.top + rect.height() * point.y / 1000f
    fun dab(x: Float, y: Float, pressure: Float?, speed: Float) {
        val speedSize = preset?.speedSize ?: 0f
        val scatter = (preset?.scatter ?: 0f) * width
        val cx = x + (random.nextFloat() - .5f) * scatter
        val cy = y + (random.nextFloat() - .5f) * scatter
        val radius = (width / 2f * ((pressure?.let { curve(preset?.sizeCurve.orEmpty(), it) } ?: 1f) +
            speedSize * speed)).coerceAtLeast(.35f)
        val grain = if (preset?.grain == true) .25f + random.nextFloat() * .75f else 1f
        val pressureAlpha = pressure?.let { curve(preset?.opacityCurve.orEmpty(), it) } ?: 1f
        val wet = preset?.wetness ?: 0f
        val opacity = (baseAlpha * pressureAlpha * grain * (1f - .65f * wet)).toInt().coerceIn(0, 255)
        paint.alpha = opacity
        when (preset?.tip) {
            "chisel", "flat", "elliptical", "knife" -> {
                val saved = canvas.save()
                canvas.rotate(preset.angle, cx, cy)
                canvas.drawOval(cx - radius, cy - radius * .4f, cx + radius, cy + radius * .4f, paint)
                canvas.restoreToCount(saved)
            }
            else -> {
                val hardness = preset?.hardness ?: 1f
                if (hardness < .9f) {
                    paint.alpha = opacity / 3
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.alpha = opacity
                }
                canvas.drawCircle(cx, cy, radius * hardness.coerceIn(.25f, 1f), paint)
            }
        }
    }
    val first = stroke.points.first()
    dab(x(first), y(first), first.pressure?.div(1000f), 0f)
    var carry = 0f
    var emitted = 1
    for (index in 1 until stroke.points.size) {
        val start = stroke.points[index - 1]
        val end = stroke.points[index]
        val dx = x(end) - x(start)
        val dy = y(end) - y(start)
        val distance = hypot(dx, dy)
        if (distance == 0f) continue
        val speed = (distance / (end.timeMs - start.timeMs).coerceAtLeast(1) / (rect.width() / 100f).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        val count = ceil(((distance + carry) / step) - 1).toInt().coerceAtMost(4800 - emitted)
        repeat(count.coerceAtLeast(0)) { dabIndex ->
            val fraction = ((step * (dabIndex + 1) - carry) / distance).coerceIn(0f, 1f)
            val pressure = end.pressure?.let { next -> ((start.pressure ?: next) + (next - (start.pressure ?: next)) * fraction) / 1000f }
            dab(x(start) + dx * fraction, y(start) + dy * fraction, pressure, speed)
            emitted++
        }
        carry = (distance + carry) % step
        if (emitted >= 4800) break
    }
    val last = stroke.points.last()
    if (last != first) dab(x(last), y(last), last.pressure?.div(1000f), 0f)
}
