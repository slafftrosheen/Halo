package com.halo.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Immutable paint snapshot for authored UI artwork; it never changes geometry or opacity. */
internal class HaloArtworkColors(scheme: ColorScheme, accent: Color = scheme.primary) {
    private val primary = accent.toArgb()
    private val background = scheme.background.toArgb()
    private val surface = scheme.surface.toArgb()
    private val foreground = scheme.onSurface.toArgb()
    private val muted = scheme.onSurfaceVariant.toArgb()
    private val dark = scheme.background.luminance() < .5f
    fun color(original: Int?): Int {
        if (original == null) return primary
        val source = Color(original)
        val spread = maxOf(source.red, source.green, source.blue) - minOf(source.red, source.green, source.blue)
        val target = when {
            source.luminance() < .04f -> if (dark) background else foreground
            spread < .1f && source.luminance() > .7f -> if (dark) foreground else surface
            spread < .1f -> muted
            else -> primary
        }
        return (target and 0x00ffffff) or (original and -0x1000000)
    }
    fun svg(source: String): String {
        val hex = Regex("#[0-9a-fA-F]{6}\\b")
        val rgb = Regex("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")
        return rgb.replace(hex.replace(source) { match ->
            "#%06x".format(color((0xff000000L or match.value.drop(1).toLong(16)).toInt()) and 0xffffff)
        }) { match ->
            val values = match.groupValues.drop(1).map(String::toInt)
            val mapped = color((0xff000000L or (values[0].toLong() shl 16) or (values[1].toLong() shl 8) or values[2].toLong()).toInt())
            "rgb(${mapped ushr 16 and 255},${mapped ushr 8 and 255},${mapped and 255})"
        }
    }
    fun lottie(source: String): String = JSONObject(source).also(::walk).toString()
    private fun paint(array: JSONArray, offset: Int) {
        val original = Color(array.getDouble(offset).toFloat(), array.getDouble(offset + 1).toFloat(), array.getDouble(offset + 2).toFloat())
        val mapped = Color(color(original.toArgb()))
        array.put(offset, mapped.red.toDouble()); array.put(offset + 1, mapped.green.toDouble()); array.put(offset + 2, mapped.blue.toDouble())
    }
    private fun animated(value: Any?, gradientStops: Int = 0) {
        if (value is JSONObject) {
            if (value.has("k")) animated(value.opt("k"), gradientStops)
            if (value.has("s")) animated(value.opt("s"), gradientStops)
            if (value.has("e")) animated(value.opt("e"), gradientStops)
        } else if (value is JSONArray && value.length() > 0) {
            if (value.opt(0) is Number) {
                if (gradientStops == 0 && value.length() >= 3) paint(value, 0)
                else for (stop in 0 until gradientStops) paint(value, stop * 4 + 1)
            } else for (index in 0 until value.length()) animated(value.opt(index), gradientStops)
        }
    }
    private fun walk(value: Any?) {
        when (value) {
            is JSONObject -> {
                when (value.optString("ty")) {
                    "fl", "st" -> animated(value.opt("c"))
                    "gf", "gs" -> value.optJSONObject("g")?.let { animated(it.opt("k"), it.optInt("p")) }
                }
                val keys = value.keys().asSequence().toList()
                keys.forEach { walk(value.opt(it)) }
            }
            is JSONArray -> for (index in 0 until value.length()) walk(value.opt(index))
        }
    }
}

@Composable
private fun rememberArtworkColors(accent: Color): HaloArtworkColors {
    val scheme = MaterialTheme.colorScheme
    return remember(accent, scheme.background, scheme.surface, scheme.onSurface, scheme.onSurfaceVariant) {
        HaloArtworkColors(scheme, accent)
    }
}

private data class ThemedLottieSource(val identity: String, val json: String?)

/** A palette change replaces the composition, clearing Lottie's cached gradient shaders.
 * Keep the old composition mounted while its same-state repaint loads so playback progress survives.
 */
@Composable
internal fun rememberHaloThemedLottie(asset: String? = null, rawRes: Int? = null,
    accent: Color = MaterialTheme.colorScheme.primary): LottieComposition? {
    require((asset != null) != (rawRes != null))
    val context = LocalContext.current
    val mapper = rememberArtworkColors(accent)
    val name = asset ?: rawRes?.let { context.resources.getResourceEntryName(it) }.orEmpty()
    val identity = name.replace(Regex("_(dark|light)(\\.json)?$"), "")
    val source by produceState<ThemedLottieSource?>(null, context, asset, rawRes, mapper) {
        value = withContext(Dispatchers.IO) {
            try {
                val text = if (asset != null) context.assets.open(asset).bufferedReader().use { it.readText() }
                    else context.resources.openRawResource(requireNotNull(rawRes)).bufferedReader().use { it.readText() }
                ThemedLottieSource(identity, mapper.lottie(text))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { ThemedLottieSource(identity, null) }
            catch (_: org.json.JSONException) { ThemedLottieSource(identity, null) }
        }
    }
    var last by remember(identity) { mutableStateOf<LottieComposition?>(null) }
    val current = source?.takeIf { it.identity == identity }
    if (current != null && current.json == null) { SideEffect { last = null }; return null }
    val json = current?.json
    val composition = if (json == null) null else rememberLottieComposition(LottieCompositionSpec.JsonString(json)).value
    SideEffect { if (composition != null) last = composition }
    return composition ?: last
}

@Composable
internal fun rememberHaloThemedSvg(asset: String, accent: Color = MaterialTheme.colorScheme.primary): ByteArray? {
    val context = LocalContext.current
    val mapper = rememberArtworkColors(accent)
    return produceState<ByteArray?>(null, context, asset, mapper) {
        value = withContext(Dispatchers.IO) {
            try { context.assets.open(asset).bufferedReader().use { mapper.svg(it.readText()).toByteArray(Charsets.UTF_8) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { null }
        }
    }.value
}
