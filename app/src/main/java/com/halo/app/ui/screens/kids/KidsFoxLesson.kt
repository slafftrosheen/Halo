package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.CanvasInkSurface
import com.halo.sdk.runtime.ink.InkCodec
import org.json.JSONObject

private data class GhostStep(val path: String, val x: Float, val y: Float, val prompt: String)

@Composable
internal fun KidsFoxLesson(target: Int, busy: Boolean, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0].language
    val steps = remember(context, locale) {
        val source = context.assets.open("kids/lessons/fox-5-steps.json").bufferedReader().use { JSONObject(it.readText()) }
        val array = source.getJSONArray("steps")
        List(array.length()) { index ->
            val step = array.getJSONObject(index)
            val start = step.getJSONArray("start")
            val text = step.getJSONObject("say")
            GhostStep(step.getString("ghost"), start.getDouble(0).toFloat(), start.getDouble(1).toFloat(),
                text.optString(locale).ifBlank { text.getString("en") })
        }
    }
    var stepIndex by remember { mutableIntStateOf(0) }
    var mirror by remember { mutableIntStateOf(1) }
    var draft by remember { mutableStateOf("") }
    val step = steps[stepIndex]
    val outline = remember(step.path) { PathParser().parsePathString(step.path).toPath() }
    val dotColor = MaterialTheme.colorScheme.tertiary
    val inkColor = MaterialTheme.colorScheme.primary
    val drawGuide: DrawScope.(Rect) -> Unit = { rect ->
        withTransform({ translate(rect.left, rect.top); scale(rect.width / 250f, rect.height / 270f, Offset.Zero) }) {
            drawPath(outline, dotColor.copy(alpha = .65f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f))))
            drawCircle(dotColor, 4f, Offset(step.x, step.y))
        }
    }
    Text(step.prompt, style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { mirror = when (mirror) { 1 -> 2; 2 -> 4; 4 -> 6; 6 -> 8; else -> 1 } },
        modifier = Modifier.heightIn(min = target.dp)) {
        Text(stringResource(R.string.lesson_mirror) + " · " + mirror)
    }
    CanvasInkSurface("", { draft = it }, aspectMilli = 926, strokeColor = inkColor,
        strokeWidthFraction = .06f, ghostGuide = drawGuide, mirrorCopies = mirror)
    val canSave = !busy && draft.length <= 48_000 && InkCodec.decode(draft)?.strokes?.isNotEmpty() == true
    KidsPrimaryButton(onClick = {
        if (stepIndex == steps.lastIndex) onSave(if (mirror == 1) draft else "mirror:$mirror;$draft") else stepIndex++
    }, modifier = Modifier.fillMaxWidth().heightIn(min = target.dp), enabled = canSave) {
        Text(if (stepIndex == steps.lastIndex) uiText("Keep drawing") else stringResource(R.string.lesson_next_step))
    }
    if (draft.length > 48_000) Text(stringResource(R.string.ink_capacity))
}

internal fun kidsLessonInk(value: String): Pair<Int, String> {
    val copies = value.takeIf { it.startsWith("mirror:") }?.substringBefore(';')?.removePrefix("mirror:")?.toIntOrNull()
    return if (copies in setOf(2, 4, 6, 8)) requireNotNull(copies) to value.substringAfter(';') else 1 to value
}
