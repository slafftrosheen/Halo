package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.InkBrushPresets
import com.halo.app.language.uiText
import com.halo.app.ui.workspace.StudioColourWheel
import com.halo.sdk.runtime.ink.InkBrushStyle
import kotlin.math.roundToInt

@Composable
internal fun CanvasBrushOptions(initialColor: Int, onChange: (InkBrushStyle?) -> Unit) {
    val brushes = remember { InkBrushPresets.drawingBrushes() }
    var id by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var tune by remember { mutableStateOf(false) }
    var chooseColor by remember { mutableStateOf(false) }
    var color by remember(initialColor) { mutableIntStateOf(initialColor) }
    var size by remember { mutableIntStateOf(6000) }
    var flow by remember { mutableIntStateOf(1000) }
    var recent by remember { mutableStateOf(emptyList<Int>()) }
    val preset = brushes.firstOrNull { it.id == id }
    val style = preset?.style(color, size, flow)
    SideEffect { onChange(style) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val favourites = remember(brushes) {
            listOf("sketch.hb-pencil", "ink.brush-pen", "paint.watercolour-wash", "marker.felt-marker",
                "air-texture.crayon-kids").mapNotNull { key -> brushes.firstOrNull { it.id == key } }
        }
        favourites.forEach {
            FilterChip(selected = it == preset, onClick = { id = it.id }, modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(uiText(it.name)) })
        }
        Box {
            TextButton(onClick = { showAll = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.draw_brushes))
            }
            DropdownMenu(showAll, { showAll = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ink_draw)) }, onClick = {
                    id = null
                    showAll = false
                    tune = false
                })
                brushes.groupBy { it.family }.forEach { (family, choices) ->
                    Text(uiText(family.replace('-', ' ').replaceFirstChar(Char::uppercase)),
                        Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                    choices.forEach {
                        DropdownMenuItem(text = { Text(uiText(it.name)) }, onClick = {
                            id = it.id
                            showAll = false
                        })
                    }
                }
            }
        }
    }
    if (preset != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { tune = !tune }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.draw_brush_editor))
            }
            TextButton(onClick = { chooseColor = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("Colour"), color = Color(color))
            }
        }
        if (tune) {
            Text(stringResource(R.string.draw_size))
            Slider(value = size.toFloat(), onValueChange = { size = it.roundToInt() }, valueRange = 1200f..80000f)
            Text(stringResource(R.string.draw_flow))
            Slider(value = flow.toFloat(), onValueChange = { flow = it.roundToInt() }, valueRange = 1f..1000f)
        }
        if (chooseColor) StudioColourWheel(color, recent.joinToString(","), onUse = {
            recent = (listOf(it) + recent).distinct().take(8)
            color = it
            chooseColor = false
        }, onDismiss = { chooseColor = false })
    }
}
