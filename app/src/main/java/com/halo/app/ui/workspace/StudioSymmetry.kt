package com.halo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioLayer
import com.halo.app.data.studio.StudioService
import com.halo.app.data.studio.StudioSymmetry
import com.halo.app.data.studio.StudioSymmetryAxis
import com.halo.app.language.uiText
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioSymmetryDialog(onChoose: (StudioSymmetry) -> Unit, onDismiss: () -> Unit) {
    var axis by remember { mutableStateOf(StudioSymmetryAxis.VERTICAL) }
    var repeats by remember { mutableIntStateOf(6) }
    val symmetry = StudioSymmetry(axis, if (axis == StudioSymmetryAxis.RADIAL) repeats else 2)
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Symmetry")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioSymmetryGuides(symmetry, Modifier.fillMaxWidth().height(110.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(StudioSymmetryAxis.VERTICAL to "Vertical", StudioSymmetryAxis.HORIZONTAL to "Horizontal",
                    StudioSymmetryAxis.RADIAL to "Radial").forEach { (choice, name) ->
                    FilterChip(axis == choice, { axis = choice }, label = { Text(uiText(name)) })
                }
            }
            if (axis == StudioSymmetryAxis.RADIAL) {
                Text(uiText("Repeats: %1\$d", repeats))
                Slider(repeats.toFloat(), { repeats = it.roundToInt() }, valueRange = 2f..12f, steps = 9)
            }
        }
    }, confirmButton = { TextButton(onClick = { onChoose(symmetry) }) { Text(uiText("Draw with symmetry")) } },
        dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}

@Composable
internal fun StudioSymmetryPreview(service: StudioService, source: StudioDocument, layer: StudioLayer) {
    val symmetry = layer.symmetry ?: return
    val document = source.copy(layers = listOf(layer), recipe = com.halo.app.data.workspace.CanvasImageRecipe(), selectedLayerId = layer.id)
    Text(uiText("Symmetry preview"), style = MaterialTheme.typography.labelMedium)
    BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val ratio = source.width.toFloat() / source.height
        val width = minOf(maxWidth, maxHeight * ratio)
        Box(Modifier.width(width).height(width / ratio)) {
            StudioDocumentPreview(service, document, Modifier.matchParentSize())
            StudioSymmetryGuides(symmetry, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun StudioSymmetryGuides(symmetry: StudioSymmetry, modifier: Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        fun guide(start: Offset, end: Offset) = drawLine(tint, start, end, strokeWidth = 1.dp.toPx(), pathEffect = dash)
        when (symmetry.axis) {
            StudioSymmetryAxis.VERTICAL -> guide(Offset(center.x, 0f), Offset(center.x, size.height))
            StudioSymmetryAxis.HORIZONTAL -> guide(Offset(0f, center.y), Offset(size.width, center.y))
            StudioSymmetryAxis.RADIAL -> repeat(symmetry.repeats) { index ->
                val angle = index * 2.0 * Math.PI / symmetry.repeats
                val length = minOf(size.width, size.height) / 2
                guide(center, center + Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat()))
            }
        }
    }
}
