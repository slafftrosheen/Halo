package com.halo.app.ui.screens.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import com.halo.app.data.notebook.CanvasSketchCodec
import com.halo.app.data.notebook.CanvasSketchPoint
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Bounded Android-native preview. Never modifies or overwrites the imported source. */
private data class InkPage(val bitmap: Bitmap, val count: Int)
private suspend fun renderInkPage(context: Context, file: WorkspaceFile, index: Int): InkPage? = withContext(Dispatchers.IO) {
    if (file.mimeType == "application/pdf") {
        context.contentResolver.openFileDescriptor(file.uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (index !in 1..renderer.pageCount) return@use null
                renderer.openPage(index - 1).use { pdfPage ->
                    val maxEdge = maxOf(pdfPage.width, pdfPage.height).coerceAtLeast(1)
                    val factor = minOf(1f, 1200f / maxEdge)
                    val bitmap = Bitmap.createBitmap((pdfPage.width * factor).toInt().coerceAtLeast(1),
                        (pdfPage.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        InkPage(bitmap, renderer.pageCount)
                    } catch (failure: Exception) { bitmap.recycle(); throw failure }
                }
            }
        }
    } else if (file.mimeType.startsWith("image/")) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
        val bitmap = context.contentResolver.openInputStream(file.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bitmap?.let { InkPage(it, 1) }
    } else null
}

/**
 * Overlay strokes use normalized source-page coordinates (0..1000).
 * The imported PDF or image remains unchanged; Save writes all modified page layers atomically.
 */
@Composable
internal fun CanvasSourceInkDialog(
    file: WorkspaceFile,
    markup: String,
    onSave: (Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val base = remember(markup) { CanvasFileMarkupCodec.decode(markup) }
    var page by remember(file.uri.toString()) { mutableIntStateOf(1) }
    val pending = remember(file.uri.toString(), markup) { mutableStateMapOf<Int, String>() }
    var error by remember { mutableStateOf(false) }
    val rendered by produceState<InkPage?>(null, file.uri.toString(), page) {
        value = try { error = false; renderInkPage(context, file, page) }
            catch (_: Exception) { error = true; null }
    }
    // State belongs to this page. Switching pages stores the previous draft in pending.
    key(page) {
        val strokes: SnapshotStateList<List<CanvasSketchPoint>> = remember(page) {
            mutableStateListOf<List<CanvasSketchPoint>>().apply {
                addAll(CanvasSketchCodec.decode(pending[page] ?: base.pages[page].orEmpty()))
            }
        }
        fun keepPage() {
            pending[page] = if (strokes.isEmpty()) "" else CanvasSketchCodec.encode(strokes.toList())
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(uiText("Annotate source")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (file.mimeType == "application/pdf") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { keepPage(); page-- }, enabled = page > 1) { Text(uiText("Previous page")) }
                        Text(uiText("Page") + " $page / ${rendered?.count ?: "…"}", Modifier.weight(1f))
                        TextButton(onClick = { keepPage(); page++ }, enabled = page < (rendered?.count ?: page)) {
                            Text(uiText("Next page"))
                        }
                    }
                    if (error) Text(uiText("Preview unavailable"), color = MaterialTheme.colorScheme.error)
                    if (rendered == null) CircularProgressIndicator()
                    else {
                        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                        Box(Modifier.fillMaxWidth().height(320.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(canvasSize, page) {
                                var active = -1
                                fun normalized(point: Offset): CanvasSketchPoint = CanvasSketchPoint(
                                    (point.x / canvasSize.width.coerceAtLeast(1) * 1000f).roundToInt().coerceIn(0, 1000),
                                    (point.y / canvasSize.height.coerceAtLeast(1) * 1000f).roundToInt().coerceIn(0, 1000))
                                detectDragGestures(
                                    onDragStart = { point ->
                                        active = if (strokes.size < CanvasSketchCodec.MAX_STROKES) {
                                            strokes.add(listOf(normalized(point))); strokes.lastIndex
                                        } else -1
                                    },
                                    onDragEnd = { active = -1 },
                                    onDragCancel = { active = -1 },
                                    onDrag = { change, _ ->
                                        if (active >= 0 && active < strokes.size) {
                                            val stroke = strokes[active]
                                            val next = normalized(change.position)
                                            if (stroke.size < CanvasSketchCodec.MAX_POINTS_PER_STROKE && next != stroke.lastOrNull()) {
                                                strokes[active] = stroke + next
                                            }
                                        }
                                        change.consume()
                                    },
                                )
                            },
                        ) {
                            Image(rendered!!.bitmap.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            CanvasSketchPreview(
                                if (strokes.isEmpty()) "" else CanvasSketchCodec.encode(strokes.toList()),
                                Modifier.fillMaxSize())
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                            enabled = strokes.isNotEmpty()) { Text(uiText("Undo stroke")) }
                        TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                            Text(uiText("Clear sketch"))
                        }
                    }
                    Text(uiText("Marks are saved on this Canvas, not into the original file."),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { keepPage(); onSave(pending.toMap()) },
                enabled = rendered != null) { Text(uiText("Save")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        )
    }
}

/** Lightweight first-page thumbnail with the same source/page-coordinate ink overlay. */
@Composable
internal fun CanvasAnnotatedFilePreview(file: WorkspaceFile, encoded: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rendered by produceState<InkPage?>(null, file.uri.toString()) {
        value = runCatching { renderInkPage(context, file, 1) }.getOrNull()
    }
    val strokes = remember(encoded) { CanvasFileMarkupCodec.decode(encoded).pages[1].orEmpty() }
    Box(modifier) {
        rendered?.let { image ->
            Image(image.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds)
            if (strokes.isNotBlank()) {
                CanvasSketchPreview(strokes, Modifier.fillMaxSize())
            }
        } ?: Text(uiText("Preview unavailable"), Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodySmall)
    }
}
