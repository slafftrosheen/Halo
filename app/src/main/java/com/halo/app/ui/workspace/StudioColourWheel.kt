package com.halo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private fun studioHue(hue: Float, saturation: Float = 1f, value: Float = 1f): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 359.999f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))

private fun studioHex(color: Int) = "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(java.util.Locale.ROOT)

@Composable
internal fun StudioColourWheel(initial: Int, recent: String, onUse: (Int) -> Unit, onDismiss: () -> Unit) {
    val starting = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableFloatStateOf(starting[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(starting[1]) }
    var value by remember(initial) { mutableFloatStateOf(starting[2]) }
    var hexText by remember(initial) { mutableStateOf(studioHex(initial)) }
    var rgbText by remember(initial) { mutableStateOf(rgbValue(initial)) }
    val colour = studioHue(hue, saturation, value)
    val choices = remember(recent) { recent.split(',').mapNotNull(String::toIntOrNull).distinct().take(8) }
    fun setHsv(nextHue: Float, nextSaturation: Float, nextValue: Float) {
        hue = nextHue; saturation = nextSaturation; value = nextValue
        val next = studioHue(hue, saturation, value)
        hexText = studioHex(next)
        rgbText = rgbValue(next)
    }
    fun setColour(next: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(next, hsv)
        setHsv(hsv[0], hsv[1], hsv[2])
    }
    val useWheel by rememberUpdatedState<(Offset, Float, Float) -> Unit> { point, width, height ->
        val center = Offset(width / 2, height / 2)
        val radius = minOf(width, height) / 2
        val dx = point.x - center.x
        val dy = point.y - center.y
        val distance = hypot(dx, dy)
        if (distance >= radius * .75f) {
            val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
            setHsv(angle, saturation, value)
        } else {
            val top = center.y - radius * .62f
            val bottom = center.y + radius * .35f
            val hueWeight = (1f - (point.y - top) / (bottom - top)).coerceIn(0f, 1f)
            val rightWeight = ((point.x - center.x) / (radius * 1.18f) + (1f - hueWeight) / 2f).coerceIn(0f, 1f - hueWeight)
            val nextValue = 1f - rightWeight
            setHsv(hue, if (nextValue <= .001f) 0f else hueWeight / nextValue, nextValue)
        }
    }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Colour wheel")) }, text = {
        Column(Modifier.heightIn(max = 535.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ringColors = remember { (0..6).map { Color(studioHue((it * 60f) % 360f)) } }
            Canvas(modifier = Modifier.align(Alignment.CenterHorizontally).size(200.dp)
                .pointerInput(Unit) { detectTapGestures { useWheel(it, size.width.toFloat(), size.height.toFloat()) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); useWheel(change.position, size.width.toFloat(), size.height.toFloat()) } }) {
                val radius = minOf(size.width, size.height) / 2
                drawCircle(Brush.sweepGradient(ringColors, center), radius * .86f, style = Stroke(radius * .20f))
                val top = Offset(center.x, center.y - radius * .62f)
                val left = Offset(center.x - radius * .59f, center.y + radius * .35f)
                val right = Offset(center.x + radius * .59f, left.y)
                val triangle = Path().apply { moveTo(top.x, top.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
                drawPath(triangle, Brush.linearGradient(listOf(Color(studioHue(hue)), Color.White), top, left))
                drawPath(triangle, Brush.linearGradient(listOf(Color.Transparent, Color.Black), Offset(left.x, left.y), right))
                val angle = Math.toRadians(hue.toDouble())
                val ringDot = center + Offset((kotlin.math.cos(angle) * radius * .86f).toFloat(), (kotlin.math.sin(angle) * radius * .86f).toFloat())
                val hint = top * (value * saturation) + left * (value * (1f - saturation)) + right * (1f - value)
                listOf(ringDot, hint).forEach { dot ->
                    drawCircle(Color.Black, 6.dp.toPx(), dot, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 6.dp.toPx(), dot, style = Stroke(1.5.dp.toPx()))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(36.dp).background(Color(colour), MaterialTheme.shapes.small))
                Text("HSV ${hue.roundToInt()}° · ${(saturation * 100).roundToInt()}% · ${(value * 100).roundToInt()}%")
            }
            Text(uiText("Hue")); Slider(hue, { setHsv(it, saturation, value) }, valueRange = 0f..359.999f)
            Text(uiText("Saturation")); Slider(saturation, { setHsv(hue, it, value) })
            Text(uiText("HSV value")); Slider(value, { setHsv(hue, saturation, it) })
            OutlinedTextField(hexText, { text ->
                hexText = text.take(7)
                hexText.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { setColour(it or 0xff000000.toInt()) }
            }, label = { Text(uiText("HEX")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rgbText, { text ->
                rgbText = text.take(15)
                val split = rgbText.split(',').map { it.trim().toIntOrNull() }
                if (split.size == 3 && split.all { it != null && it in 0..255 })
                    setColour(android.graphics.Color.rgb(requireNotNull(split[0]), requireNotNull(split[1]), requireNotNull(split[2])))
            }, label = { Text(uiText("RGB (0–255, comma-separated)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (choices.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                choices.forEach { swatch ->
                    TextButton(onClick = { setColour(swatch) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.size(24.dp).background(Color(swatch), MaterialTheme.shapes.small))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onUse(colour) }) { Text(uiText("Use colour")) } },
        dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}

private fun rgbValue(colour: Int) = "${android.graphics.Color.red(colour)}, ${android.graphics.Color.green(colour)}, ${android.graphics.Color.blue(colour)}"
