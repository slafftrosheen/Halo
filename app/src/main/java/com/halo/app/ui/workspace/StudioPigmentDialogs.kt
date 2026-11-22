package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioService
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

internal data class StudioPigment(val color: Int, val width: Int)

private val defaultPigments = listOf(0xff18203a, 0xfff3f2f8, 0xff5a54a0, 0xff3f775f, 0xffe8ac78, 0xffab627b, 0xff4e79b8, 0xff86715d).map(Long::toInt)
private fun recentPigments(stored: String) = stored.split(',').mapNotNull(String::toIntOrNull)
internal fun studioRecentPigments(stored: String, color: Int): String = (listOf(color) + recentPigments(stored)).distinct().take(8).joinToString(",")

@Composable
internal fun StudioEyedropperDialog(service: StudioService, document: StudioDocument, ready: Boolean,
    onUse: (Int) -> Unit, onDismiss: () -> Unit) {
    val selected = document.layers.firstOrNull { it.id == document.selectedLayerId && it.visible }
    var currentLayer by remember(document) { mutableStateOf(false) }
    var sampled by remember(document) { mutableStateOf<Int?>(null) }
    var radius by remember(document) { mutableIntStateOf(0) }
    var failed by remember(document, currentLayer) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, document, currentLayer) {
        value = null
        try {
            val source = if (currentLayer && selected != null) document.copy(background = 0, layers = listOf(selected)) else document
            value = service.previewDocument(source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Eyedropper")) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected != null) FilterChip(currentLayer, { currentLayer = !currentLayer; sampled = null },
                label = { Text(uiText("Sample only the selected layer")) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 px", "3 × 3", "5 × 5").forEachIndexed { index, label ->
                    FilterChip(radius == index, { radius = index }, label = { Text(label) })
                }
            }
            val rendered = bitmap
            if (rendered != null) Image(rendered.asImageBitmap(), uiText("Tap the picture to pick a colour"),
                Modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(rendered.width.toFloat() / rendered.height)
                    .pointerInput(rendered, radius, ready) {
                        if (ready) detectTapGestures { point ->
                            val x = (point.x / size.width * rendered.width).toInt().coerceIn(0, rendered.width - 1)
                            val y = (point.y / size.height * rendered.height).toInt().coerceIn(0, rendered.height - 1)
                            sampled = samplePigment(rendered, x, y, radius)
                        }
                    }, contentScale = ContentScale.FillBounds)
            else if (failed) Text(stringResource(R.string.studio_preview_error)) else CircularProgressIndicator()
            sampled?.let { color ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)))
                    Text(pigmentHex(color))
                }
            }
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
        }
    }, confirmButton = {
        TextButton(onClick = { sampled?.let(onUse) }, enabled = ready && sampled != null) { Text(uiText("Use for next stroke")) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

private fun samplePigment(bitmap: Bitmap, x: Int, y: Int, radius: Int): Int {
    val channels = IntArray(4)
    var count = 0
    for (row in maxOf(0, y - radius)..minOf(bitmap.height - 1, y + radius)) {
        for (column in maxOf(0, x - radius)..minOf(bitmap.width - 1, x + radius)) {
            val color = bitmap.getPixel(column, row)
            channels[0] += android.graphics.Color.alpha(color)
            channels[1] += android.graphics.Color.red(color)
            channels[2] += android.graphics.Color.green(color)
            channels[3] += android.graphics.Color.blue(color)
            count++
        }
    }
    return android.graphics.Color.argb(channels[0] / count, channels[1] / count, channels[2] / count, channels[3] / count)
}

private fun pigmentHex(color: Int) = "#" + color.toUInt().toString(16).uppercase(java.util.Locale.ROOT).padStart(8, '0')

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioQuickPaletteDialog(stored: String, current: Int?, ready: Boolean,
    onUse: (StudioPigment) -> Unit, onDismiss: () -> Unit) {
    val choices = (recentPigments(stored) + defaultPigments).distinct().take(8)
    var pigment by remember(stored, current) { mutableStateOf(pigmentHex(current ?: choices.first())) }
    var width by remember { mutableIntStateOf(5) }
    val color = pigment.removePrefix("#").takeIf { it.length in setOf(6, 8) }?.toUIntOrNull(16)?.let {
        if (pigment.removePrefix("#").length == 6) (it or 0xFF000000u).toInt() else it.toInt()
    }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Quick palette")) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    val label = pigmentHex(choice)
                    Surface(Modifier.padding(2.dp).size(44.dp).clip(CircleShape).clickable { pigment = label }
                        .semantics { contentDescription = label }, shape = CircleShape, color = Color(choice),
                        border = BorderStroke(if (color == choice) 3.dp else 1.dp, MaterialTheme.colorScheme.outline)) {}
                }
            }
            OutlinedTextField(pigment, { pigment = it.take(9) }, singleLine = true, label = { Text(uiText("HEX colour")) }, isError = color == null)
            Text(uiText("Brush width"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Fine", 3 to "Small", 5 to "Medium", 9 to "Wide", 15 to "Bold", 25 to "Broad").forEach { (size, label) ->
                    FilterChip(width == size, { width = size }, label = { Text(uiText(label)) })
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { color?.let { onUse(StudioPigment(it, width)) } }, enabled = ready && color != null) { Text(uiText("Paint on a new layer")) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
