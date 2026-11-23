package com.halo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import android.graphics.RectF
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import kotlin.math.roundToInt

private val coordinate = Regex("""^[0-9]+(?:\.[0-9]+)?$""")
private fun Float.nodeText() = java.math.BigDecimal(toString()).stripTrailingZeros().toPlainString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioVectorDialog(
    doc: StudioDocument,
    original: StudioLayer?,
    initialShape: StudioShape,
    onApply: (StudioLayer) -> Unit,
    onDismiss: () -> Unit,
) {
    val layer = remember(original, initialShape) {
        original ?: StudioLayer(name = initialShape.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            kind = StudioLayerKind.RECTANGLE, bounds = StudioRect(200, 200, 600, 600))
    }
    val initial = remember(layer, initialShape) { layer.vector ?: StudioVectorGeometry.default(initialShape) }
    var unit by remember { mutableStateOf(StudioLengthUnit.MM) }
    var shape by remember { mutableStateOf(initial.shape) }
    var vector by remember { mutableStateOf(initial) }
    var failure by remember { mutableStateOf(false) }
    val axes = remember(doc) { listOf(doc.width, doc.height, doc.width, doc.height) }
    val bounds = remember(layer) { listOf(layer.bounds.x, layer.bounds.y, layer.bounds.width, layer.bounds.height) }
    fun fields(unit: StudioLengthUnit) = bounds.mapIndexed { i, value ->
        java.math.BigDecimal.valueOf(unit.fromPixels(value * axes[i] / 1000.0, doc.dpi))
            .setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    var position by remember { mutableStateOf(fields(unit)) }
    var nodesInput by remember(initial) { mutableStateOf(initial.nodes.joinToString("\n") { node ->
        listOf(node.x, node.y, node.incomingX, node.incomingY, node.outgoingX, node.outgoingY).joinToString(" ") { it.nodeText() }
    }) }
    fun readNodes(): List<StudioPathNode> = nodesInput.lineSequence().filter { it.isNotBlank() }.map { row ->
        val numbers = row.trim().split(Regex("""\s+"""))
        require(numbers.size == 2 || numbers.size == 6)
        val parsed = numbers.map { require(coordinate.matches(it)); it.toFloat() }
        if (parsed.size == 2) StudioPathNode(parsed[0], parsed[1])
        else StudioPathNode(parsed[0], parsed[1], parsed[2], parsed[3], parsed[4], parsed[5])
    }.take(129).toList()
    val preview = remember(vector, nodesInput) {
        if (shape != StudioShape.PATH) vector
        else runCatching { vector.copy(nodes = readNodes()) }.getOrDefault(vector)
    }
    val previewColor = MaterialTheme.colorScheme.primary
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Precision drawing")) }, text = {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val padding = 12.dp.toPx()
                val box = RectF(padding, padding, size.width - padding, size.height - padding)
                if (box.width() > 0 && box.height() > 0) {
                    val path = preview.path(box).asComposePath()
                    if (preview.outlineOnly) drawPath(path, previewColor, style = Stroke(3.dp.toPx()))
                    else drawPath(path, previewColor)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StudioShape.entries.forEach { option ->
                    FilterChip(option == shape, {
                        if (option != shape) {
                            shape = option
                            vector = StudioVectorGeometry.default(option)
                            nodesInput = vector.nodes.joinToString("\n") { node ->
                                listOf(node.x, node.y, node.incomingX, node.incomingY, node.outgoingX, node.outgoingY)
                                    .joinToString(" ") { it.nodeText() }
                            }
                        }
                    }, label = { Text(uiText(option.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase))) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StudioLengthUnit.entries.forEach { option ->
                    FilterChip(option == unit, {
                        val converted = position.mapIndexed { i, input ->
                            val old = unit.fromPixels(bounds[i] * axes[i] / 1000.0, doc.dpi)
                            studioQuantity(input, old)?.let { quantity -> option.fromPixels(unit.toPixels(quantity, doc.dpi), doc.dpi) }
                        }
                        if (converted.all { it != null }) {
                            unit = option
                            position = converted.map { value -> java.math.BigDecimal.valueOf(requireNotNull(value))
                                .setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }
                            failure = false
                        } else failure = true
                    }, label = { Text(option.label) })
                }
            }
            listOf(R.string.studio_x, R.string.studio_y, R.string.studio_width, R.string.studio_height).forEachIndexed { i, label ->
                StudioNumberField(stringResource(label), position[i], { value ->
                    position = position.toMutableList().also { it[i] = value }; failure = false
                }, suffix = unit.label)
            }
            when (shape) {
                StudioShape.ROUNDED_RECT, StudioShape.FRAME -> {
                    val labels = listOf("Top left", "Top right", "Bottom right", "Bottom left")
                    vector.corners.forEachIndexed { i, radius ->
                        Text(uiText(labels[i]) + " · " + (radius * 100).roundToInt().toString() + "%")
                        Slider(radius, { value -> vector = vector.copy(corners = vector.corners.toMutableList().also { it[i] = value }) },
                            valueRange = 0f..0.5f)
                    }
                }
                StudioShape.POLYGON, StudioShape.STAR -> {
                    Text(uiText("Points") + " · " + vector.sides)
                    Slider(vector.sides.toFloat(), { vector = vector.copy(sides = it.roundToInt()) }, valueRange = 3f..60f, steps = 56)
                    if (shape == StudioShape.STAR) {
                        Text(uiText("Inner radius"))
                        Slider(vector.innerRadius, { vector = vector.copy(innerRadius = it) }, valueRange = .05f..1f)
                    }
                }
                StudioShape.ARC -> {
                    Text(uiText("Start angle") + " · " + vector.startAngle.roundToInt() + "°")
                    Slider(vector.startAngle, { vector = vector.copy(startAngle = it) }, valueRange = -360f..360f)
                    Text(uiText("Sweep") + " · " + vector.sweep.roundToInt() + "°")
                    Slider(vector.sweep, { vector = vector.copy(sweep = it) }, valueRange = 1f..360f)
                }
                StudioShape.PATH -> {
                    OutlinedTextField(nodesInput, { nodesInput = it.take(8000); failure = false },
                        label = { Text(stringResource(R.string.vec_nodes)) }, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(uiText("One node per line: x y, then optional incoming and outgoing x y. Values from 0 to 1.")) },
                        minLines = 3, maxLines = 7)
                    FilterChip(vector.closed, { vector = vector.copy(closed = !vector.closed) },
                        label = { Text(stringResource(R.string.vec_close_path)) })
                }
                else -> Unit
            }
            if (vector.outlineOnly) {
                Text(stringResource(R.string.studio_stroke_width))
                Slider(vector.stroke, { vector = vector.copy(stroke = it) }, valueRange = .001f..0.15f)
                FilterChip(vector.dash, { vector = vector.copy(dash = !vector.dash) }, label = { Text(uiText("Dashed")) })
            }
            if (failure) Text(stringResource(R.string.studio_invalid_fields), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = {
            runCatching {
                val values = position.mapIndexed { i, input ->
                    val previous = unit.fromPixels(bounds[i] * axes[i] / 1000.0, doc.dpi)
                    val raw = requireNotNull(studioQuantity(input, previous))
                    (unit.toPixels(raw, doc.dpi) * 1000.0 / axes[i]).roundToInt()
                }
                layer.copy(bounds = StudioRect(values[0], values[1], values[2], values[3]),
                    vector = if (shape == StudioShape.PATH) vector.copy(nodes = readNodes()) else vector).also(StudioLayer::requireValid)
            }.onSuccess(onApply).onFailure { failure = true }
        }) { Text(stringResource(R.string.studio_apply)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}
