package com.halo.app.ui.screens.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.data.notebook.CanvasDrawingDraft
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded Android-native preview. Never modifies or overwrites the imported source. */
internal data class InkPage(val bitmap: Bitmap, val count: Int, val page: Int = 1)
internal suspend fun renderInkPage(context: Context, file: WorkspaceFile, index: Int, maxDimension: Int = 1200): InkPage? {
    var owned: Bitmap? = null
    try {
        val result = withContext(Dispatchers.IO) {
    if (file.mimeType == "application/pdf") {
        context.contentResolver.openFileDescriptor(file.uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (index !in 1..renderer.pageCount) return@use null
                renderer.openPage(index - 1).use { pdfPage ->
                    val maxEdge = maxOf(pdfPage.width, pdfPage.height).coerceAtLeast(1)
                    val factor = minOf(1f, maxDimension.toFloat() / maxEdge)
                    val bitmap = Bitmap.createBitmap((pdfPage.width * factor).toInt().coerceAtLeast(1),
                        (pdfPage.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    owned = bitmap
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        InkPage(bitmap, renderer.pageCount, index)
                    } catch (failure: Exception) { bitmap.recycle(); throw failure }
                }
            }
        }
    } else if (file.mimeType.startsWith("image/")) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val bitmap = context.contentResolver.openInputStream(file.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bitmap?.let { owned = it; InkPage(it, 1) }
    } else null
        }
        owned = null
        return result
    } finally { owned?.takeUnless { it.isRecycled }?.recycle() }
}

/** Source-page coordinates share the editor/preview transform. Original file bytes are never written. */
@Composable
internal fun CanvasSourceInkDialog(
    file: WorkspaceFile,
    markup: String,
    recovery: CanvasDrawingDraft? = null,
    onDraft: (CanvasDrawingDraft) -> Unit = {},
    onSave: (Map<Int, String>) -> Unit,
    onSaveSelection: (com.halo.sdk.runtime.ink.InkSelectionContext, Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
    onSelectionChanged: (com.halo.sdk.runtime.ink.InkSelectionContext?) -> Unit = {},
) {
    val context = LocalContext.current
    val base = remember(markup) { CanvasFileMarkupCodec.decode(markup) }
    val readable = !markup.startsWith("halo-markup:") || base.title != markup
    var page by remember(file.uri.toString()) { mutableIntStateOf(if (file.mimeType == "application/pdf") (recovery?.page ?: 1).coerceIn(1, 9999) else 1) }
    val pending = remember(file.uri.toString(), markup) { mutableStateMapOf<Int, String>().apply { putAll(recovery?.pages.orEmpty()) } }
    val validRecovery = pending.size <= CanvasFileMarkupCodec.MAX_PAGES && pending.all { (index, value) ->
        index in 1..9999 && InkCodec.decode(value) != null
    }
    var error by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val rendered by produceState<InkPage?>(null, file.uri.toString(), file.version, page) {
        value = null
        value = try {
            error = false
            renderInkPage(context, file, page).also { if (it == null) error = true }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { error = true; null }
    }
    val currentPage = rendered?.takeIf { it.page == page }
    val displayedBitmap = rendered?.bitmap
    DisposableEffect(displayedBitmap) { onDispose { displayedBitmap?.recycle() } }
    val selectionCallback by rememberUpdatedState(onSelectionChanged)
    LaunchedEffect(file.objectId.value, page) {
        selectionCallback(null)
    }
    fun draftFor(pages: Map<Int, String>) = CanvasDrawingDraft(recovery?.nodeId, "annotation", markup,
        pages = pages, page = page, fileVersion = recovery?.fileVersion)
    fun keepPosition() {
        if (readable && validRecovery) onDraft(draftFor(pending.toMap()))
    }
    key(file.uri.toString(), page) {
        // Stable across draft callbacks; revisiting a page loads its latest completed local draft.
        val originalPage = remember { pending[page] ?: base.pages[page].orEmpty() }
        AlertDialog(
            modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(horizontal = 16.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(uiText("Annotate source")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (file.mimeType == "application/pdf") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { page--; keepPosition() }, enabled = page > 1) { Text(uiText("Previous page")) }
                        Text(uiText("Page") + " $page / ${currentPage?.count ?: "…"}", Modifier.weight(1f))
                        TextButton(onClick = { page++; keepPosition() }, enabled = page < (currentPage?.count ?: page) && page < 9999) {
                            Text(uiText("Next page"))
                        }
                    }
                    if (!readable || !validRecovery) Text(stringResource(R.string.ink_unreadable), color = MaterialTheme.colorScheme.error)
                    if (error) Text(uiText("Preview unavailable"), color = MaterialTheme.colorScheme.error)
                    if (currentPage == null && !error) CircularProgressIndicator()
                    currentPage?.takeIf { readable && validRecovery }?.let { image ->
                        CanvasInkSurface(originalPage, background = image.bitmap.asImageBitmap(), onChanged = { encoded ->
                            val doc = requireNotNull(InkCodec.decode(encoded))
                            val updates = pending.toMap() + (page to if (doc.strokes.isEmpty()) "" else encoded)
                            // Verify both aggregate envelopes before accepting this whole edit.
                            CanvasFileMarkupCodec.merge(markup, updates)
                            val draft = draftFor(updates)
                            draft.encode()
                            onDraft(draft)
                            pending[page] = updates.getValue(page)
                            saveError = false
                        }, selectionOwnerId = file.version?.let { file.objectId.value }, selectionPageId = "page-$page",
                            selectionBaseRevision = file.version ?: file.sha256,
                            onSelectionChanged = onSelectionChanged,
                            onSaveSelection = { selection ->
                                try {
                                    val updates = pending.toMap()
                                    CanvasFileMarkupCodec.merge(markup, updates)
                                    draftFor(updates).encode()
                                    onSaveSelection(selection, updates)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                  catch (_: Exception) { saveError = true }
                            })
                    }
                    if (saveError) Text(stringResource(R.string.ink_draft_rejected), color = MaterialTheme.colorScheme.error)
                    Text(uiText("Marks are saved on this Canvas, not into the original file."), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ink_draft_help), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = {
                try {
                    val updates = pending.toMap()
                    CanvasFileMarkupCodec.merge(markup, updates)
                    draftFor(updates).encode()
                    onSave(updates)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                  catch (_: Exception) { saveError = true }
            }, enabled = currentPage != null && readable && validRecovery) { Text(uiText("Save")) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        )
    }
}

/** Lightweight first-page thumbnail with the same source/page-coordinate ink overlay. */
@Composable
internal fun CanvasAnnotatedFilePreview(file: WorkspaceFile, encoded: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rendered by produceState<InkPage?>(null, file.uri.toString(), file.version) {
        value = null
        value = try { renderInkPage(context, file, 1, maxDimension = 512) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
    }
    val previewBitmap = rendered?.bitmap
    DisposableEffect(previewBitmap) { onDispose { previewBitmap?.recycle() } }
    val strokes = remember(encoded) { CanvasFileMarkupCodec.decode(encoded).pages[1].orEmpty() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        rendered?.let { image ->
            val aspect = image.bitmap.width.toFloat() / image.bitmap.height
            val fittedWidth = minOf(maxWidth, maxHeight * aspect)
            Box(Modifier.width(fittedWidth).height(fittedWidth / aspect)) {
                Image(image.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds)
                if (strokes.isNotBlank()) CanvasSketchPreview(strokes, Modifier.fillMaxSize())
            }
        } ?: Text(uiText("Preview unavailable"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall)
    }
}
