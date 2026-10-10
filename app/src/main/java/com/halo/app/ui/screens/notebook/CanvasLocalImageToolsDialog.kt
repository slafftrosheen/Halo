package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.app.data.workspace.CanvasImageRecipe
import com.halo.app.data.workspace.CanvasImageTools
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasLocalImageToolsDialog(
    file: WorkspaceFile,
    busy: Boolean,
    onSave: (CanvasImageRecipe) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var recipe by remember(file.uri, file.version) { mutableStateOf(CanvasImageRecipe()) }
    var previewFailed by remember(file.uri, file.version) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, file.uri, file.version, recipe) {
        value = null
        previewFailed = false
        try { value = CanvasImageTools.preview(context, file, recipe) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { previewFailed = true }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(uiText("Image tools")) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(uiText("Edit locally. Your original image remains unchanged."),
                    style = MaterialTheme.typography.bodySmall)
                if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(185.dp), contentScale = ContentScale.Fit)
                else Text(uiText(if (previewFailed) "Image preview unavailable" else "Loading preview…"),
                    style = MaterialTheme.typography.bodySmall)
                Text(file.displayName.take(90), style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 3) % 4)
                    }, enabled = !busy) { Text(uiText("Rotate left")) }
                    OutlinedButton(onClick = {
                        recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 1) % 4)
                    }, enabled = !busy) { Text(uiText("Rotate right")) }
                    OutlinedButton(onClick = {
                        recipe = recipe.copy(mirrorHorizontal = !recipe.mirrorHorizontal)
                    }, enabled = !busy) {
                        Text(uiText(if (recipe.mirrorHorizontal) "Undo mirror" else "Mirror horizontally"))
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = recipe.squareCrop, onCheckedChange = {
                        if (!busy) recipe = recipe.copy(squareCrop = it)
                    }, enabled = !busy)
                    Text(uiText("Center square crop"))
                }
                Text(uiText("Maximum output size"), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(512, 1024, 2048, 4096).forEach { edge ->
                        FilterChip(selected = recipe.maxEdge == edge,
                            onClick = { recipe = recipe.copy(maxEdge = edge) }, enabled = !busy,
                            label = { Text("$edge px") })
                    }
                }
                Text(uiText("Image format"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasImageFormat.entries.forEach { format ->
                        FilterChip(selected = recipe.format == format,
                            onClick = { recipe = recipe.copy(format = format) }, enabled = !busy,
                            label = { Text(format.name) })
                    }
                }
                Text(uiText("PNG retains transparency; JPEG uses a white background."),
                    style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(recipe) }, enabled = !busy && bitmap != null && !previewFailed) {
                Text(uiText("Save new image"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) }
        },
    )
}
