package com.halo.app.ui.toolkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.CanvasSketchPadDialog
import com.halo.app.ui.screens.notebook.CanvasSketchPreview
import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class ScanFormField(val label: String, val bounds: Rect)

@Composable
internal fun ScanPageOrderDialog(current: Int, pageCount: Int, onClose: () -> Unit, onRotate: () -> Unit, onMove: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(uiText("Rotate and reorder pages")) }, text = {
        Column {
            TextButton(onClick = onRotate) { Text(uiText("Rotate 90°")) }
            TextButton(enabled = current > 0 && current < pageCount, onClick = { onMove(current - 1) }) { Text(uiText("Move earlier")) }
            TextButton(enabled = current + 1 < pageCount, onClick = { onMove(current + 1) }) { Text(uiText("Move later")) }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(uiText("Done")) } })
}

@Composable
internal fun ScanFilterDialog(onClose: () -> Unit, onFilter: (ScanPaperFilter) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(uiText("Paper filter")) }, text = {
        Column {
            ScanPaperFilter.entries.forEach { filter ->
                TextButton(onClick = { onFilter(filter) }) { Text(uiText(filter.label)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(uiText("Done")) } })
}

@Composable
internal fun ScanCropDialog(onClose: () -> Unit, onApply: (Float) -> Unit) {
    var inset by remember { mutableFloatStateOf(.05f) }
    AlertDialog(onDismissRequest = onClose, title = { Text(uiText("Trim page edges")) }, text = {
        Column {
            Text(uiText("Trim %1\$d%% from each edge", (inset * 100).toInt()))
            Slider(inset, { inset = it }, valueRange = 0f..0.4f)
        }
    }, confirmButton = { TextButton(onClick = { onApply(inset) }) { Text(uiText("Apply")) } })
}

internal fun scanFormFields(lines: List<HaloScanLine>, width: Int): List<ScanFormField> = lines.mapNotNull { line ->
    val bounds = line.position ?: return@mapNotNull null
    val label = line.text.trim().trimEnd('_', ' ', '…').trim()
    if (!label.endsWith(':') || label.length !in 2..80 || label.none(Char::isLetter) || bounds.right >= width * .82f) {
        return@mapNotNull null
    }
    ScanFormField(label.removeSuffix(":"), Rect(bounds))
}.distinctBy { it.bounds }.take(20)

internal fun fillScanForm(source: Bitmap, entries: List<Pair<ScanFormField, String>>): Bitmap {
    val output = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(22, 36, 67) }
    entries.forEach { (field, answer) ->
        val rect = field.bounds
        val left = rect.right.coerceAtLeast(0) + 8f
        val room = output.width - left - 6f
        if (room > 0 && rect.bottom in 1..output.height) {
            paint.textSize = rect.height().coerceIn(10, 80) * .88f
            val required = paint.measureText(answer)
            if (required > room) paint.textSize *= room / required
            canvas.drawText(answer, left, rect.bottom.toFloat(), paint)
        }
    }
    return output
}

@Composable
internal fun HaloScanFormEditor(fields: List<ScanFormField>, onClose: () -> Unit,
    onApply: (List<Pair<ScanFormField, String>>) -> Unit) {
    val values = remember(fields) { mutableStateMapOf<Int, String>() }
    AlertDialog(onDismissRequest = onClose, title = { Text(uiText("Review form fields")) },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fields.isEmpty()) Text(uiText("No clear labels ending in a colon were found on this page."))
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(values[index].orEmpty(), { values[index] = it.take(180) }, singleLine = true,
                        label = { Text(field.label) })
                }
            }
        }, confirmButton = { TextButton(enabled = values.values.any { it.isNotBlank() }, onClick = {
            onApply(fields.mapIndexedNotNull { i, field -> values[i]?.trim()?.takeIf(String::isNotEmpty)?.let { field to it } })
        }) { Text(uiText("Apply to scan")) } }, dismissButton = { TextButton(onClick = onClose) { Text(uiText("Cancel")) } })
}

private const val SIGNATURE = "halo_scan_signature"

@Composable
internal fun HaloScanSignatureEditor(onClose: () -> Unit, onApply: (String, Float, Float, Float) -> Unit) {
    val scope = rememberCoroutineScope()
    var signature by remember { mutableStateOf("") }
    var drawing by remember { mutableStateOf(false) }
    var saveSignature by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(.25f) }
    var left by remember { mutableFloatStateOf(.88f) }
    var top by remember { mutableFloatStateOf(.88f) }
    LaunchedEffect(Unit) {
        try {
            val revision = Graph.kidsPreferences.adultActionRevision()
            signature = Graph.secretStore.get(SIGNATURE).orEmpty()
            Graph.kidsPreferences.requireAdultAction(revision)
            saved = signature.isNotEmpty()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { signature = ""; error = true }
        finally { busy = false }
    }
    if (drawing) {
        CanvasSketchPadDialog(initial = signature, onDismiss = { drawing = false }, secure = true, onSave = {
            if (it.length <= 48_000) { signature = it; drawing = false } else { drawing = false; error = true }
        })
        return
    }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(uiText("Sign this scan")) },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (signature.isNotBlank()) CanvasSketchPreview(signature, Modifier.fillMaxWidth().height(70.dp))
                TextButton(onClick = { drawing = true }, enabled = !busy) { Text(uiText("Draw signature")) }
                if (signature.isNotBlank()) {
                    Text(uiText("Size")); Slider(size, { size = it }, valueRange = .12f..0.45f, enabled = !busy)
                    Text(uiText("Left to right")); Slider(left, { left = it }, enabled = !busy)
                    Text(uiText("Top to bottom")); Slider(top, { top = it }, enabled = !busy)
                    FilterChip(saveSignature, onClick = { saveSignature = !saveSignature },
                        label = { Text(uiText("Save the signature on this phone")) }, enabled = !busy)
                }
                if (saved) TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val revision = Graph.kidsPreferences.adultActionRevision()
                            Graph.kidsPreferences.requireAdultAction(revision)
                            Graph.secretStore.remove(SIGNATURE)
                            signature = ""; saved = false; saveSignature = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(uiText("Forget saved signature")) }
                if (error) Text(uiText("Could not open or save the signature."), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = !busy && signature.isNotBlank(), onClick = {
            busy = true
            scope.launch {
                try {
                    val revision = Graph.kidsPreferences.adultActionRevision()
                    Graph.kidsPreferences.requireAdultAction(revision)
                    if (saveSignature) Graph.secretStore.put(SIGNATURE, signature)
                    Graph.kidsPreferences.requireAdultAction(revision)
                    onApply(signature, size, left, top)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }) { Text(uiText("Apply signature")) } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(uiText("Cancel")) } })
}

internal fun stampScanSignature(source: Bitmap, encoded: String, size: Float, horizontal: Float, vertical: Float): Bitmap {
    val ink = requireNotNull(InkCodec.decode(encoded))
    val points = ink.strokes.flatMap { it.points }
    require(points.isNotEmpty())
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val inkWidth = (points.maxOf { it.x } - minX).coerceAtLeast(1)
    val inkHeight = (points.maxOf { it.y } - minY).coerceAtLeast(1)
    val width = source.width * size.coerceIn(.12f, .45f)
    val height = (width * inkHeight / inkWidth / (ink.aspectMilli / 1000f)).coerceIn(1f, (source.height * .30f).coerceAtLeast(1f))
    val startX = 4 + ((source.width - width - 8).coerceAtLeast(0f)) * horizontal.coerceIn(0f, 1f)
    val startY = 4 + ((source.height - height - 8).coerceAtLeast(0f)) * vertical.coerceIn(0f, 1f)
    fun x(value: Int) = startX + (value - minX) * width / inkWidth
    fun y(value: Int) = startY + (value - minY) * height / inkHeight
    val output = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 32, 64); strokeWidth = (width * .012f).coerceAtLeast(2f)
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    ink.strokes.forEach { stroke ->
        val first = stroke.points.first()
        if (stroke.points.size == 1) canvas.drawPoint(x(first.x), y(first.y), paint) else {
            val path = Path().apply {
                moveTo(x(first.x), y(first.y))
                stroke.points.drop(1).forEach { lineTo(x(it.x), y(it.y)) }
            }
            canvas.drawPath(path, paint)
        }
    }
    return output
}
