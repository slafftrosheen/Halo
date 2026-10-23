package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioCommand
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioService
import com.halo.app.data.workspace.CanvasImageCrop

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioTransformDialog(service: StudioService, document: StudioDocument,
    onApply: (StudioCommand) -> Unit, onDismiss: () -> Unit) {
    var recipe by remember(document) { mutableStateOf(document.recipe) }
    var width by remember(document) { mutableStateOf(document.width.toString()) }
    var height by remember(document) { mutableStateOf(document.height.toString()) }
    val crop = remember(document) { (document.recipe.crop ?: CanvasImageCrop(100, 100, 800, 800)).let {
        listOf(it.x, it.y, it.width, it.height).map { value -> mutableStateOf((value / 10f).toString()) }
    } }
    var custom by remember(document) { mutableStateOf(document.recipe.crop != null) }
    var invalid by remember { mutableStateOf(false) }
    fun draft(): StudioDocument {
        val selectedCrop = if (custom) studioRect(crop).let { CanvasImageCrop(it.x, it.y, it.width, it.height) } else null
        return document.copy(width = width.toInt(), height = height.toInt(), recipe = recipe.copy(crop = selectedCrop,
            squareCrop = recipe.squareCrop && !custom)).also(StudioDocument::requireValid)
    }
    val preview = runCatching { draft() }.getOrNull()
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.studio_transforms)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview?.let { StudioDocumentPreview(service, it, Modifier.fillMaxWidth().height(160.dp)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 3) % 4) }) {
                        Text(stringResource(R.string.studio_rotate_left))
                    }
                    OutlinedButton(onClick = { recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 1) % 4) }) {
                        Text(stringResource(R.string.studio_rotate_right))
                    }
                    FilterChip(recipe.mirrorHorizontal, { recipe = recipe.copy(mirrorHorizontal = !recipe.mirrorHorizontal) },
                        label = { Text(stringResource(R.string.studio_mirror)) })
                }
                FilterChip(recipe.squareCrop, {
                    custom = false; recipe = recipe.copy(squareCrop = !recipe.squareCrop, crop = null)
                }, label = { Text(stringResource(R.string.studio_crop)) })
                FilterChip(custom, { custom = !custom; recipe = recipe.copy(squareCrop = false, crop = null) },
                    label = { Text(stringResource(R.string.studio_custom_crop)) })
                if (custom) {
                    Text(stringResource(R.string.studio_crop_area), style = MaterialTheme.typography.titleSmall)
                    StudioRectFields(crop)
                }
                Text(stringResource(R.string.studio_resize), style = MaterialTheme.typography.titleSmall)
                StudioNumberField(stringResource(R.string.studio_width), width, { width = it }, "px")
                StudioNumberField(stringResource(R.string.studio_height), height, { height = it }, "px")
                Text(stringResource(R.string.studio_output_size), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(512, 1024, 2048, 4096).forEach { edge ->
                        FilterChip(recipe.maxEdge == edge, { recipe = recipe.copy(maxEdge = edge) }, label = { Text("$edge px") })
                    }
                }
                if (invalid || preview == null) Text(stringResource(R.string.studio_size_error), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = {
            runCatching { draft() }.onSuccess { result ->
                onApply(StudioCommand.Batch(listOf(StudioCommand.Resize(result.width, result.height),
                    StudioCommand.Transform(result.recipe)))); onDismiss()
            }.onFailure { invalid = true }
        }, enabled = preview != null) { Text(stringResource(R.string.studio_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}
