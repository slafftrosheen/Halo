package com.halo.app.ui.screens.notebook

import com.halo.app.ui.workspace.StudioAlertDialog as WorkspaceToolDialog

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.data.notebook.CanvasDrawingDraft
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.app.ui.workspace.withWorkspacePreviewDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded Android-native preview. Never modifies or overwrites the imported source. */
internal data class InkPage(val bitmap: Bitmap, val count: Int, val page: Int = 1)
internal suspend fun renderInkPage(context: Context, file: WorkspaceFile, index: Int, maxDimension: Int = 1200): InkPage? {
    var owned: Bitmap? = null
    val limit = maxDimension.coerceIn(128, 1536)
    try {
        val result = withWorkspacePreviewDecode {
            withContext(Dispatchers.IO) {
                when {
                    file.mimeType == "application/pdf" -> context.contentResolver.openFileDescriptor(file.uri, "r")?.use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            if (index !in 1..renderer.pageCount) return@withContext null
                            renderer.openPage(index - 1).use { page ->
                                val factor = limit.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1),
                                    (page.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                owned = bitmap
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                InkPage(bitmap, renderer.pageCount, index)
                            }
                        }
                    }
                    file.mimeType.startsWith("image/") -> {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@withContext null
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
                        val bitmap = context.contentResolver.openInputStream(file.uri)?.use {
                            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                            })
                        }
                        bitmap?.let { owned = it; InkPage(it, 1) }
                    }
                    else -> null
                }
            }
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
    onSaveSelection: ((com.halo.sdk.runtime.ink.InkSelectionContext, Map<Int, String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSelectionChanged: (com.halo.sdk.runtime.ink.InkSelectionContext?) -> Unit = {},
    scopeLabel: String? = null,
) {
    val context = LocalContext.current
    val sourceRecovery = recovery?.takeIf { it.matchesSource(it.nodeId, "annotation", markup, file.version) }
    val base = remember(markup) { CanvasFileMarkupCodec.decode(markup) }
    val readable = !markup.startsWith("halo-markup:") || base.title != markup
    var page by remember(file.uri.toString()) { mutableIntStateOf(if (file.mimeType == "application/pdf") (sourceRecovery?.page ?: 1).coerceIn(1, 9999) else 1) }
    val pending = remember(file.uri.toString(), markup) { mutableStateMapOf<Int, String>().apply { putAll(sourceRecovery?.pages.orEmpty()) } }
    val validRecovery = pending.size <= CanvasFileMarkupCodec.MAX_PAGES && pending.all { (index, value) ->
        index in 1..9999 && InkCodec.decode(value) != null
    }
    var error by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val rendered by key(file.uri.toString(), file.version, page) { produceState<InkPage?>(null) {
        value = null
        value = try {
            error = false
            renderInkPage(context, file, page).also { if (it == null) error = true }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { error = true; null }
    } }
    val currentPage = rendered?.takeIf { it.page == page }
    val displayedBitmap = rendered?.bitmap
    // Published pixels are collected only after retained rendering references end.
    val selectionCallback by rememberUpdatedState(onSelectionChanged)
    LaunchedEffect(file.objectId.value, page) {
        selectionCallback(null)
    }
    fun draftFor(pages: Map<Int, String>) = CanvasDrawingDraft(sourceRecovery?.nodeId, "annotation", markup,
        pages = pages, page = page, fileVersion = file.version)
    fun keepPosition() {
        if (readable && validRecovery) onDraft(draftFor(pending.toMap()))
    }
    key(file.uri.toString(), page) {
        // Stable across draft callbacks; revisiting a page loads its latest completed local draft.
        val originalPage = remember { pending[page] ?: base.pages[page].orEmpty() }
        WorkspaceToolDialog(
            maxWidth = 880.dp,
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
                            onSaveSelection = onSaveSelection?.let { save -> { selection ->
                                try {
                                    val updates = pending.toMap()
                                    CanvasFileMarkupCodec.merge(markup, updates)
                                    draftFor(updates).encode()
                                    save(selection, updates)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                  catch (_: Exception) { saveError = true }
                            } })
                    }
                    if (saveError) Text(stringResource(R.string.ink_draft_rejected), color = MaterialTheme.colorScheme.error)
                    Text(scopeLabel ?: uiText("Marks are saved on this Canvas, not into the original file."), style = MaterialTheme.typography.bodySmall)
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
internal fun CanvasAnnotatedFilePreview(file: WorkspaceFile, encoded: String, modifier: Modifier = Modifier,
    page: Int = 1, maxDimension: Int = 512, onPageCount: (Int) -> Unit = {}, onAspectRatio: (Float) -> Unit = {}) {
    val context = LocalContext.current
    var loading by remember(file.uri.toString(), file.version, page, maxDimension) { mutableStateOf(true) }
    var retry by remember(file.uri.toString(), file.version, page, maxDimension) { mutableIntStateOf(0) }
    val rendered by key(file.uri.toString(), file.version, page, maxDimension, retry) { produceState<InkPage?>(null) {
        loading = true
        value = null
        try { value = renderInkPage(context, file, page, maxDimension) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
        finally { loading = false }
    } }
    val previewBitmap = rendered?.bitmap
    // Published pixels are collected only after retained rendering references end.
    LaunchedEffect(rendered) { rendered?.let { onPageCount(it.count); onAspectRatio(it.bitmap.width.toFloat() / it.bitmap.height) } }
    val strokes = remember(encoded, page) { CanvasFileMarkupCodec.decode(encoded).pages[page].orEmpty() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        rendered?.let { image ->
            val aspect = image.bitmap.width.toFloat() / image.bitmap.height
            val fittedWidth = minOf(maxWidth, maxHeight * aspect)
            Box(Modifier.width(fittedWidth).height(fittedWidth / aspect).testTag("canvas-source-fit")) {
                Image(image.bitmap.asImageBitmap(), contentDescription = if (file.mimeType == "application/pdf")
                    stringResource(R.string.canvas50_page, image.page, image.count) else file.displayName, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds)
                if (strokes.isNotBlank()) CanvasSketchPreview(strokes, Modifier.fillMaxSize().testTag("canvas-source-ink"))
            }
        } ?: if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(uiText("Preview unavailable"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { retry++ }) { Text(uiText("Try again")) }
        }
    }
}
