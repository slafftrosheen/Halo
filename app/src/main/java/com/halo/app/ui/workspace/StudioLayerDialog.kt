package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioLayerDialog(layer: StudioLayer, onApply: (StudioLayer) -> Unit, onDismiss: () -> Unit) {
    var name by remember(layer) { mutableStateOf(layer.name) }
    var text by remember(layer) { mutableStateOf(layer.text) }
    var color by remember(layer) { mutableStateOf("#" + layer.color.toUInt().toString(16).padStart(8, '0')) }
    var visible by remember(layer) { mutableStateOf(layer.visible) }
    var locked by remember(layer) { mutableStateOf(layer.locked) }
    var opacity by remember(layer) { mutableFloatStateOf(layer.opacity.toFloat()) }
    var brightness by remember(layer) { mutableFloatStateOf(layer.brightness.toFloat()) }
    var contrast by remember(layer) { mutableFloatStateOf(layer.contrast.toFloat()) }
    var saturation by remember(layer) { mutableFloatStateOf(layer.saturation.toFloat()) }
    var rotation by remember(layer) { mutableStateOf(layer.rotation.toString()) }
    var fontSize by remember(layer) { mutableStateOf((layer.fontSize / 10f).toString()) }
    var strokeWidth by remember(layer) { mutableStateOf((layer.strokeWidth / 10f).toString()) }
    var advanced by remember { mutableStateOf(false) }
    var masked by remember(layer) { mutableStateOf(layer.mask != null) }
    val geometry = remember(layer) { listOf(layer.bounds.x, layer.bounds.y, layer.bounds.width, layer.bounds.height)
        .map { mutableStateOf((it / 10f).toString()) } }
    val mask = remember(layer) { (layer.mask ?: StudioRect(100, 100, 800, 800)).let {
        listOf(it.x, it.y, it.width, it.height).map { value -> mutableStateOf((value / 10f).toString()) }
    } }
    var invalid by remember { mutableStateOf(false) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.studio_edit_layer)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.studio_layer_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !locked)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(visible, { visible = !visible }, label = { Text(stringResource(R.string.studio_visible)) }, enabled = !locked)
                    FilterChip(locked, { locked = !locked }, label = { Text(stringResource(R.string.studio_locked)) })
                }
                if (layer.kind == StudioLayerKind.TEXT) {
                    OutlinedTextField(text, { text = it.take(2000) }, label = { Text(stringResource(R.string.studio_text)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, enabled = !locked)
                    StudioNumberField(stringResource(R.string.studio_font_size), fontSize, { fontSize = it }, "%", enabled = !locked)
                }
                if (layer.kind != StudioLayerKind.IMAGE) {
                    StudioColorChoices(color, { color = it }, enabled = !locked)
                    if (advanced) OutlinedTextField(color, { color = it.take(9) },
                        label = { Text(stringResource(R.string.studio_color)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), enabled = !locked)
                }
                if (layer.kind == StudioLayerKind.INK) StudioNumberField(stringResource(R.string.studio_stroke_width),
                    strokeWidth, { strokeWidth = it }, "%", enabled = !locked)
                StudioSlider(stringResource(R.string.studio_opacity), opacity, 0f..1000f, !locked) { opacity = it }
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.studio_more)) }
                if (advanced) {
                    Text(stringResource(R.string.studio_position), style = MaterialTheme.typography.titleSmall)
                    StudioRectFields(geometry, !locked)
                    StudioNumberField(stringResource(R.string.studio_rotation), rotation, { rotation = it }, "°", enabled = !locked)
                    StudioSlider(stringResource(R.string.studio_brightness), brightness, -1000f..1000f, !locked) { brightness = it }
                    StudioSlider(stringResource(R.string.studio_contrast), contrast, 0f..3000f, !locked) { contrast = it }
                    StudioSlider(stringResource(R.string.studio_saturation), saturation, 0f..3000f, !locked) { saturation = it }
                    FilterChip(masked, { masked = !masked }, label = { Text(stringResource(R.string.studio_mask)) }, enabled = !locked)
                    if (masked) {
                        Text(stringResource(R.string.studio_mask_help), style = MaterialTheme.typography.bodySmall)
                        StudioRectFields(mask, !locked)
                    }
                }
                if (invalid) Text(stringResource(R.string.studio_invalid_fields), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching {
                require(color.matches(Regex("#[0-9A-Fa-f]{8}")))
                layer.copy(name = name, text = text, color = color.drop(1).toLong(16).toInt(), visible = visible, locked = locked,
                    opacity = opacity.roundToInt(), brightness = brightness.roundToInt(), contrast = contrast.roundToInt(),
                    saturation = saturation.roundToInt(), rotation = rotation.toInt(), fontSize = (fontSize.replace(',', '.').toFloat() * 10).roundToInt(),
                    strokeWidth = (strokeWidth.replace(',', '.').toFloat() * 10).roundToInt(), bounds = studioRect(geometry), mask = if (masked) studioRect(mask) else null)
                    .also(StudioLayer::requireValid)
            }.onSuccess { onApply(it); onDismiss() }.onFailure { invalid = true }
        }) { Text(stringResource(R.string.studio_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}

@Composable
internal fun StudioNumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String = "", enabled: Boolean = true) {
    OutlinedTextField(value, { onChange(it.take(12)) }, label = { Text(label) }, singleLine = true,
        suffix = { if (suffix.isNotEmpty()) Text(suffix) }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
@Composable
private fun StudioSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    Text("$label ${(value / 10).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
    Slider(value, onChange, valueRange = range, enabled = enabled)
}
@Composable
internal fun StudioRectFields(values: List<MutableState<String>>, enabled: Boolean = true) {
    val labels = listOf(R.string.studio_x, R.string.studio_y, R.string.studio_width, R.string.studio_height)
    labels.forEachIndexed { index, label -> StudioNumberField(stringResource(label), values[index].value,
        { values[index].value = it }, "%", enabled) }
}
internal fun studioRect(values: List<MutableState<String>>): StudioRect {
    val numbers = values.map { it.value.replace(',', '.').toFloat().also { number -> require(number.isFinite() && number in -1000f..1000f) } }
    return StudioRect((numbers[0] * 10).roundToInt(), (numbers[1] * 10).roundToInt(),
        (numbers[2] * 10).roundToInt(), (numbers[3] * 10).roundToInt())
}
