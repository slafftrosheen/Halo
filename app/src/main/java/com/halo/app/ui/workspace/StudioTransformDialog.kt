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
import com.halo.app.language.uiText
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioTransformDialog(service: StudioService, document: StudioDocument,
    onApply: (StudioCommand) -> Unit, onDismiss: () -> Unit, cropOnly: Boolean = false) {
    var recipe by remember(document) { mutableStateOf(document.recipe) }
    var width by remember(document) { mutableStateOf(document.width.toString()) }
    var height by remember(document) { mutableStateOf(document.height.toString()) }
    val crop = remember(document) { (document.recipe.crop ?: CanvasImageCrop(100, 100, 800, 800)).let {
        listOf(it.x, it.y, it.width, it.height).map { value -> mutableStateOf((value / 10f).toString()) }
    } }
    var custom by remember(document, cropOnly) { mutableStateOf(document.recipe.crop != null || cropOnly && !document.recipe.squareCrop) }
    var invalid by remember { mutableStateOf(false) }
    fun draft(): StudioDocument {
        val selectedCrop = if (custom) studioRect(crop).let { CanvasImageCrop(it.x, it.y, it.width, it.height) } else null
        return document.copy(width = width.toInt(), height = height.toInt(), recipe = recipe.copy(crop = selectedCrop,
            squareCrop = recipe.squareCrop && !custom)).also(StudioDocument::requireValid)
    }
    val preview = runCatching { draft() }.getOrNull()
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (cropOnly) R.string.studio_crop else R.string.studio_transforms)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview?.let { StudioDocumentPreview(service, it, Modifier.fillMaxWidth().height(160.dp)) }
                if (!cropOnly) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 3) % 4) }) {
                        Text(stringResource(R.string.studio_rotate_left))
                    }
                    OutlinedButton(onClick = { recipe = recipe.copy(quarterTurns = (recipe.quarterTurns + 1) % 4) }) {
                        Text(stringResource(R.string.studio_rotate_right))
                    }
                    FilterChip(recipe.mirrorHorizontal, { recipe = recipe.copy(mirrorHorizontal = !recipe.mirrorHorizontal) },
                        label = { Text(stringResource(R.string.studio_mirror)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cropOnly) {
                        TextButton(onClick = { custom = false; recipe = recipe.copy(squareCrop = false, crop = null) }) { Text(uiText("Original")) }
                        listOf("1:1" to 1f, "4:5" to 0.8f, "16:9" to 16f / 9f, "A4" to 210f / 297f).forEach { (label, ratio) ->
                            TextButton(onClick = {
                                val artboardRatio = document.width.toFloat() / document.height
                                val cropWidth = minOf(1000, (1000 * ratio / artboardRatio).roundToInt()).coerceAtLeast(1)
                                val cropHeight = minOf(1000, (1000 * artboardRatio / ratio).roundToInt()).coerceAtLeast(1)
                                listOf((1000 - cropWidth) / 2, (1000 - cropHeight) / 2, cropWidth, cropHeight)
                                    .forEachIndexed { index, value -> crop[index].value = (value / 10f).toString() }
                                recipe = recipe.copy(squareCrop = false, crop = null)
                                custom = true
                            }) { Text(label) }
                        }
                    } else FilterChip(recipe.squareCrop, {
                        custom = false; recipe = recipe.copy(squareCrop = !recipe.squareCrop, crop = null)
                    }, label = { Text(stringResource(R.string.studio_crop)) })
                    FilterChip(custom, { custom = !custom; recipe = recipe.copy(squareCrop = false, crop = null) },
                        label = { Text(stringResource(R.string.studio_custom_crop)) })
                }
                if (custom) {
                    Text(stringResource(R.string.studio_crop_area), style = MaterialTheme.typography.titleSmall)
                    StudioRectFields(crop)
                }
                if (!cropOnly) {
                    Text(stringResource(R.string.studio_resize), style = MaterialTheme.typography.titleSmall)
                    StudioNumberField(stringResource(R.string.studio_width), width, { width = it }, "px")
                    StudioNumberField(stringResource(R.string.studio_height), height, { height = it }, "px")
                    Text(stringResource(R.string.studio_output_size), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(512, 1024, 2048, 4096).forEach { edge ->
                            FilterChip(recipe.maxEdge == edge, { recipe = recipe.copy(maxEdge = edge) }, label = { Text("$edge px") })
                        }
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
