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
import com.halo.app.data.workspace.CanvasImageStitcher
import com.halo.app.data.workspace.CanvasStitchBackground
import com.halo.app.data.workspace.CanvasStitchMode
import com.halo.app.data.workspace.CanvasStitchRecipe
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

/** Exactly the selected sources, with an offline preview; never an implicit whole-board export. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasImageStitchDialog(
    sources: List<Pair<String, WorkspaceFile>>,
    busy: Boolean,
    onSave: (CanvasStitchRecipe) -> Unit,
    onDismiss: () -> Unit,
) {
    val files = sources.map { it.second }
    val key = files.map { it.objectId.value + "|" + it.version }
    val context = LocalContext.current
    var recipe by remember(key) { mutableStateOf(CanvasStitchRecipe()) }
    var previewError by remember(key, recipe) { mutableStateOf(false) }
    val preview by produceState<Bitmap?>(null, key, recipe) {
        value = null
        try { value = CanvasImageStitcher.preview(context, files, recipe) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { previewError = true }
    }
    DisposableEffect(preview) { onDispose { preview?.recycle() } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(uiText("Combine images")) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Combine selected images locally. Originals stay unchanged."),
                    style = MaterialTheme.typography.bodySmall)
                Text(files.mapIndexed { i, file -> (i + 1).toString() + ". " + file.displayName.take(60) }.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall)
                if (preview != null) {
                    Image(preview!!.asImageBitmap(), contentDescription = null,
                        Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 235.dp),
                        contentScale = ContentScale.Fit)
                } else {
                    Text(uiText(if (previewError) "Image preview unavailable" else "Loading preview…"),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(uiText("Layout"), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasStitchMode.entries.forEach { mode ->
                        val label = when (mode) {
                            CanvasStitchMode.HORIZONTAL -> "Side by side"
                            CanvasStitchMode.VERTICAL -> "Stack vertically"
                            CanvasStitchMode.GRID -> "Grid"
                        }
                        FilterChip(selected = recipe.mode == mode,
                            onClick = { recipe = recipe.copy(mode = mode) }, enabled = !busy,
                            label = { Text(uiText(label)) })
                    }
                }
                Text(uiText("Spacing"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 8, 24).forEach { px ->
                        FilterChip(selected = recipe.gap == px,
                            onClick = { recipe = recipe.copy(gap = px) }, enabled = !busy,
                            label = { Text("$px px") })
                    }
                }
                Text(uiText("Maximum output size"), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1024, 2048, 4096).forEach { px ->
                        FilterChip(selected = recipe.maxEdge == px,
                            onClick = { recipe = recipe.copy(maxEdge = px) }, enabled = !busy,
                            label = { Text("$px px") })
                    }
                }
                Text(uiText("Image format"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasImageFormat.entries.forEach { format ->
                        FilterChip(selected = recipe.format == format,
                            onClick = {
                                recipe = recipe.copy(format = format,
                                    background = if (format == CanvasImageFormat.JPEG)
                                        CanvasStitchBackground.WHITE else recipe.background)
                            }, enabled = !busy,
                            label = { Text(format.name) })
                    }
                }
                Text(uiText("Background"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasStitchBackground.entries.forEach { background ->
                        FilterChip(selected = recipe.background == background,
                            onClick = { recipe = recipe.copy(background = background) },
                            enabled = !busy && (recipe.format == CanvasImageFormat.PNG ||
                                background == CanvasStitchBackground.WHITE),
                            label = { Text(uiText(if (background == CanvasStitchBackground.WHITE)
                                "White" else "Transparent")) })
                    }
                }
                if (recipe.format == CanvasImageFormat.JPEG) {
                    Text(uiText("PNG retains transparency; JPEG uses a white background."),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(uiText("Images are arranged in Canvas reading order (top to bottom, then left to right)."),
                    style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(recipe) }, enabled = !busy && preview != null && !previewError) {
                Text(uiText("Save combined image"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) } },
    )
}
