package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.language.uiText
import com.halo.app.ui.workspace.WorkspaceAttachmentPreview

internal fun WorkspaceFile.isCanvasVisualSource() = mimeType.startsWith("image/") || mimeType == "application/pdf"

internal fun WorkspaceFile.isCanvasPreviewAvailable() = availability == WorkspaceFileAvailability.AVAILABLE ||
    availability == WorkspaceFileAvailability.OWNED_LOCAL

internal data class CanvasMediaSource(val attachment: NoteAttachment?, val file: WorkspaceFile?)

/** Canonical file/annotation state wins over a secondary attachment projection. */
internal fun canvasMediaSource(attachment: NoteAttachment?, file: WorkspaceFile?): CanvasMediaSource =
    if (file?.isCanvasVisualSource() == true) CanvasMediaSource(null, file) else CanvasMediaSource(attachment, null)

/** The card keeps source selection separate from the explicit inspection action. */
@Composable
internal fun CanvasMediaCard(
    title: String, attachment: NoteAttachment?, file: WorkspaceFile?, markup: String,
    previewEnabled: Boolean, canOpenSource: Boolean, onSelect: () -> Unit, onOpenSource: () -> Unit,
) {
    val source = canvasMediaSource(attachment, file)
    // Replacing a source/version or its annotations closes the old read-only view.
    val sourceKey = listOf(source.attachment, source.file?.uri, source.file?.version, source.file?.availability, markup)
    var inspecting by remember(sourceKey) { mutableStateOf(false) }
    val available = source.attachment != null || source.file?.isCanvasPreviewAvailable() == true
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onSelect(); inspecting = true }, enabled = available) {
                Icon(Icons.Default.OpenInFull, stringResource(R.string.canvas50_inspect))
            }
            IconButton(onClick = onOpenSource, enabled = canOpenSource) {
                Icon(Icons.Default.OpenInNew, uiText("Open source"))
            }
        }
        val selectLabel = stringResource(R.string.canvas50_select)
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("canvas-media-preview")
            .clickable(onClickLabel = selectLabel, onClick = onSelect), contentAlignment = Alignment.Center) {
            when {
                !available -> Text(uiText("Source unavailable"), style = MaterialTheme.typography.bodySmall)
                !previewEnabled -> Text(stringResource(R.string.canvas50_deferred), Modifier.padding(6.dp),
                    style = MaterialTheme.typography.bodySmall)
                source.attachment != null -> WorkspaceAttachmentPreview(source.attachment, Modifier.fillMaxSize(),
                    maxDimension = 512, inspectable = false)
                source.file != null -> CanvasAnnotatedFilePreview(source.file, markup, Modifier.fillMaxSize())
            }
        }
    }
    if (inspecting && available) CanvasMediaInspection(title, source.attachment, source.file, markup,
        canOpenSource, onOpenSource, onDismiss = { inspecting = false })
}

/** Read-only display state. No save operation, file write, new route or permission owner. */
@Composable
internal fun CanvasMediaInspection(
    title: String, attachment: NoteAttachment?, file: WorkspaceFile?, markup: String,
    canOpenSource: Boolean, onOpenSource: () -> Unit, onDismiss: () -> Unit,
) {
    val source = canvasMediaSource(attachment, file)
    val sourceKey = listOf(source.attachment, source.file?.uri, source.file?.version, markup)
    var page by remember(sourceKey) { mutableIntStateOf(1) }
    var pageCount by remember(sourceKey) { mutableIntStateOf(1) }
    var aspect by remember(sourceKey, page) { mutableFloatStateOf(1f) }
    var transform by remember(sourceKey, page) { mutableStateOf(CanvasInspectionTransform()) }
    val hostDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Android Dialog has its own density; retain the user's app text scale.
        CompositionLocalProvider(LocalDensity provides hostDensity) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, uiText("Close")) }
                    }
                    val zoomDescription = stringResource(R.string.canvas50_zoom_level, (transform.scale * 100).roundToInt())
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("canvas-inspection-viewport")
                        .semantics { stateDescription = zoomDescription }) {
                        val width = with(hostDensity) { maxWidth.toPx() }
                        val height = with(hostDensity) { maxHeight.toPx() }
                        LaunchedEffect(width, height, aspect) {
                            transform = transformCanvasInspection(transform, width, height, aspect)
                        }
                        Box(Modifier.fillMaxSize().pointerInput(sourceKey, page, width, height, aspect) {
                            detectTransformGestures { center, pan, zoom, _ ->
                                transform = transformCanvasInspection(transform, width, height, aspect, zoom,
                                    pan.x, pan.y, center.x - width / 2, center.y - height / 2)
                            }
                        }) {
                            val mediaModifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = transform.scale; scaleY = transform.scale
                                translationX = transform.x; translationY = transform.y
                            }
                            if (source.attachment != null) WorkspaceAttachmentPreview(source.attachment, mediaModifier,
                                maxDimension = 1536, inspectable = false, onAspectRatio = { aspect = it })
                            else if (source.file?.isCanvasPreviewAvailable() == true) CanvasAnnotatedFilePreview(source.file, markup, mediaModifier,
                                page = page, maxDimension = 1536,
                                onPageCount = { pageCount = it }, onAspectRatio = { aspect = it })
                        }
                    }
                    if (source.attachment == null && source.file?.mimeType == "application/pdf") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { page-- }, enabled = page > 1) {
                                Icon(Icons.Default.ChevronLeft, stringResource(R.string.canvas50_previous_page))
                            }
                            Text(stringResource(R.string.canvas50_page, page, pageCount), Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { page++ }, enabled = page < pageCount) {
                                Icon(Icons.Default.ChevronRight, stringResource(R.string.canvas50_next_page))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { transform = transform.copy(scale = (transform.scale / 1.5f).coerceAtLeast(1f), x = 0f, y = 0f) }, enabled = transform.scale > 1f) {
                            Icon(Icons.Default.ZoomOut, uiText("Zoom out"))
                        }
                        IconButton(onClick = { transform = CanvasInspectionTransform() }) {
                            Icon(Icons.Default.FitScreen, stringResource(R.string.canvas50_fit))
                        }
                        IconButton(onClick = { transform = transform.copy(scale = (transform.scale * 1.5f).coerceAtMost(4f), x = 0f, y = 0f) },
                            enabled = transform.scale < 4f) { Icon(Icons.Default.ZoomIn, uiText("Zoom in")) }
                        IconButton(onClick = onOpenSource, enabled = canOpenSource) {
                            Icon(Icons.Default.OpenInNew, uiText("Open source"))
                        }
                    }
                }
            }
        }
    }
}
