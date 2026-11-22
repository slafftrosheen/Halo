package com.halo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.CanvasLocalFileThumbnail
import kotlin.math.roundToInt

@Composable
internal fun StudioReferenceDialog(files: List<WorkspaceFile>, active: WorkspaceFile?, opacity: Float, alwaysOnTop: Boolean,
    loading: Boolean, onOpacity: (Float) -> Unit, onAlwaysOnTop: (Boolean) -> Unit, onChoose: (WorkspaceFile) -> Unit,
    onBrowse: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Reference image")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active != null) {
                Text(active.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(uiText("Opacity %1\$d%%", (opacity * 100).roundToInt()))
                Slider(opacity, onOpacity, valueRange = .15f..1f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(uiText("Keep visible while drawing"), Modifier.weight(1f))
                    Switch(alwaysOnTop, onAlwaysOnTop)
                }
                TextButton(onClick = onRemove) { Text(uiText("Unpin reference")) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = onBrowse, enabled = !loading) { Text(uiText("Choose an image file")) }
            if (files.isNotEmpty()) {
                Text(uiText("Workspace images"), style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 237.dp)) {
                    items(files, key = { it.objectId.value }) { file ->
                        TextButton(onClick = { onChoose(file) }, enabled = !loading && file != active,
                            modifier = Modifier.fillMaxWidth()) { Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(uiText("Done")) } }, dismissButton = {})
}

@Composable
internal fun StudioReferenceOverlay(file: WorkspaceFile, opacity: Float, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 142.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CanvasLocalFileThumbnail(file, Modifier.fillMaxWidth().height(94.dp).alpha(opacity))
            TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 5.dp), modifier = Modifier.heightIn(min = 36.dp)) {
                Text(uiText("Unpin reference"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
