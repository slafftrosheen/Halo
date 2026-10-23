package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkSelectionContext

@Composable
internal fun CanvasSketchPreview(encoded: String, modifier: Modifier = Modifier,
    selection: InkSelectionContext? = null) {
    val document = remember(encoded, selection) {
        val source = InkCodec.decode(encoded) ?: return@remember null
        if (selection == null) source else {
            val bounds = selection.bounds
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            val selectedIds = selection.selectedStrokeIds.toSet()
            val selected = source.strokes.filter { it.strokeId in selectedIds }
                .map { stroke -> stroke.copy(points = stroke.points.map { point ->
                    point.copy(
                        x = 40 + (point.x - bounds.left) * 920 / width,
                        y = 40 + (point.y - bounds.top) * 920 / height,
                    )
                }) }
            val cropAspect = if (source.aspectMilli == 0) 0 else
                (source.aspectMilli.toFloat() * width / height).toInt().coerceIn(125, 8000)
            source.copy(strokes = selected, aspectMilli = cropAspect)
        }
    }
    val ink = if (selection == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        document?.let {
            val rect = inkBounds(size.width, size.height, it.aspectMilli)
            if (selection == null) drawInk(it, ink, rect)
            else clipRect(rect.left, rect.top, rect.right, rect.bottom) { drawInk(it, ink, rect) }
        }
    }
}

@Composable
internal fun CanvasSketchPadDialog(
    initial: String = "",
    recovery: String? = null,
    selectionOwnerId: String? = null,
    selectionPageId: String? = null,
    onSelectionChanged: (com.halo.sdk.runtime.ink.InkSelectionContext?) -> Unit = {},
    onSaveSelection: (com.halo.sdk.runtime.ink.InkSelectionContext) -> Unit = {},
    onDraft: (String) -> Unit = {},
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    editingExisting: Boolean = false,
) {
    // Keep the source stable while the parent receives drafts. Unchanged v1 is saved byte-for-byte.
    val source = remember(initial) { recovery ?: initial }
    var draft by remember(source) { mutableStateOf(source) }
    val document = remember(draft) { InkCodec.decode(draft) }
    LaunchedEffect(Unit) { onSelectionChanged(null) }
    AlertDialog(
        modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(uiText("Sketch")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CanvasInkSurface(source, onChanged = { encoded -> onDraft(encoded); draft = encoded },
                    selectionOwnerId = selectionOwnerId, selectionPageId = selectionPageId,
                    onSelectionChanged = onSelectionChanged, onSaveSelection = onSaveSelection)
                Text(stringResource(R.string.ink_draft_help), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            com.halo.app.ui.components.HaloActionButton(onClick = { onSave(draft) },
                enabled = document != null && (editingExisting || document.strokes.isNotEmpty())) { Text(uiText("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}
