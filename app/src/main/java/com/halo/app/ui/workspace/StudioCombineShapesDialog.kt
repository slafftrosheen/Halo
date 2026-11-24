package com.halo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CombinePreview(val command: StudioCommand.CombineShapes, val layer: StudioLayer?, val error: String? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioCombineShapesDialog(document: StudioDocument, onApply: (StudioCommand.CombineShapes) -> Unit, onDismiss: () -> Unit) {
    val candidates = remember(document) { document.layers.asReversed().filter { it.canCombineShape() } }
    if (candidates.size < 2) return
    val defaultTarget = candidates.find { it.id == document.selectedLayerId } ?: candidates.first()
    var target by remember { mutableStateOf(defaultTarget) }
    var other by remember { mutableStateOf(candidates.first { it.id != defaultTarget.id }) }
    var operation by remember { mutableStateOf(StudioShapeOperation.WELD) }
    var keep by remember { mutableStateOf(document.layers.size < StudioDocument.MAX_LAYERS) }
    val command = remember(target.id, other.id, operation, keep) { StudioCommand.CombineShapes(target.id, other.id, operation, keep) }
    val result by produceState<CombinePreview?>(null, document, command) {
        value = try {
            val edited = withContext(Dispatchers.Default) { document.apply(command) }
            CombinePreview(command, edited.layers.first { it.id == edited.selectedLayerId })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CombinePreview(command, null, "The shapes have no usable result for this operation.")
        }
    }
    val preview = result?.takeIf { it.command == command }
    val ink = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.outlineVariant
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Combine shapes")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeOperand(uiText("Target"), target, candidates) { next ->
                if (next.id == other.id) other = target
                target = next
            }
            ShapeOperand(uiText("Second shape"), other, candidates.filter { it.id != target.id }) { other = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioShapeOperation.entries.forEach { choice ->
                    val label = androidx.compose.ui.res.stringResource(when (choice) {
                        StudioShapeOperation.WELD -> com.halo.app.R.string.mk13_studio_join
                        StudioShapeOperation.TRIM -> com.halo.app.R.string.mk13_studio_cut_out
                        StudioShapeOperation.INTERSECT -> com.halo.app.R.string.mk13_studio_overlap
                    })
                    FilterChip(operation == choice, { operation = choice }, label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (operation == StudioShapeOperation.TRIM) Text(uiText("Trim removes the second shape from the target."),
                style = MaterialTheme.typography.bodySmall)
            if (document.layers.size < StudioDocument.MAX_LAYERS) {
                FilterChip(keep, { keep = !keep }, label = { Text(uiText("Keep originals as hidden layers")) },
                    modifier = Modifier.heightIn(min = 48.dp))
            } else Text(uiText("This project is at its layer limit. The result replaces the two selected shapes."),
                style = MaterialTheme.typography.bodySmall)
            if (preview == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            preview?.layer?.let { layer ->
                if (operation == StudioShapeOperation.WELD) com.halo.app.ui.theme.v13.Halo13Animation(
                    com.halo.app.ui.theme.v13.Halo13Motion.JOIN_SHAPES, Modifier.fillMaxWidth().height(64.dp),
                )
                Canvas(Modifier.fillMaxWidth().height(152.dp)) {
                    val fit = minOf(size.width / document.width, size.height / document.height)
                    val width = document.width * fit
                    val height = document.height * fit
                    withTransform({
                        translate((size.width - width) / 2, (size.height - height) / 2)
                        scale(fit, fit, Offset.Zero)
                    }) {
                        drawRect(paper, size = androidx.compose.ui.geometry.Size(document.width.toFloat(), document.height.toFloat()),
                            style = Stroke(1.dp.toPx() / fit.coerceAtLeast(.001f)))
                        drawPath(requireNotNull(layer.vector).path(layer.bounds.vectorPixels(document.width, document.height)).asComposePath(), ink)
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onApply(command) }, enabled = preview?.layer != null) { Text(uiText("Apply")) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } })
}

@Composable
private fun ShapeOperand(label: String, current: StudioLayer, choices: List<StudioLayer>, onChoose: (StudioLayer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(current.name)
            }
            DropdownMenu(expanded, { expanded = false }) {
                choices.forEach { layer -> DropdownMenuItem(text = { Text(layer.name) }, onClick = {
                    expanded = false
                    onChoose(layer)
                }) }
            }
        }
    }
}
