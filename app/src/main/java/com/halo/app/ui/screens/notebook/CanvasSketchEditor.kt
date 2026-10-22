package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.sdk.runtime.ink.InkCodec

@Composable
internal fun CanvasSketchPreview(encoded: String, modifier: Modifier = Modifier) {
    val document = remember(encoded) { InkCodec.decode(encoded) }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        document?.let { drawInk(it, ink, inkBounds(size.width, size.height, it.aspectMilli)) }
    }
}

@Composable
internal fun CanvasSketchPadDialog(
    initial: String = "",
    recovery: String? = null,
    selectionOwnerId: String? = null,
    selectionPageId: String? = null,
    onSelectionChanged: (com.halo.sdk.runtime.ink.InkSelectionContext?) -> Unit = {},
    onDraft: (String) -> Unit = {},
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
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
                    onSelectionChanged = onSelectionChanged)
                Text(stringResource(R.string.ink_draft_help), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            com.halo.app.ui.components.HaloActionButton(onClick = { onSave(draft) },
                enabled = document?.strokes?.isNotEmpty() == true) { Text(uiText("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}
