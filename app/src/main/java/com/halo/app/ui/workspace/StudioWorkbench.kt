package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.*

/** The artboard owns the working area; manual tools and the current layer have stable positions. */
@Composable
internal fun StudioWorkbench(service: StudioService, project: StudioProject, busy: Boolean,
    onDraw: () -> Unit, onText: () -> Unit, onShapes: () -> Unit, onImage: () -> Unit,
    onLayers: () -> Unit, onTransform: () -> Unit, onSelect: (String) -> Unit, onEdit: (StudioLayer) -> Unit,
    modifier: Modifier = Modifier) {
    val document = project.session.current
    val selected = document.layers.firstOrNull { it.id == document.selectedLayerId }
    val checker = MaterialTheme.colorScheme.surfaceContainerHigh
    if (com.halo.app.ui.presence.LocalShellHaloOrb.current) {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = document.width.toFloat() / document.height
            val width = minOf(maxWidth, maxHeight * ratio)
            Surface(Modifier.width(width).height(width / ratio), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                StudioDocumentPreview(service, document, Modifier.fillMaxSize().drawBehind {
                    val tile = 14.dp.toPx()
                    for (row in 0..(size.height / tile).toInt()) for (column in 0..(size.width / tile).toInt()) {
                        if ((row + column) % 2 == 0) drawRect(checker, Offset(column * tile, row * tile), Size(tile, tile))
                    }
                })
            }
        }
        return
    }
    BoxWithConstraints(modifier) {
    val short = maxHeight < 420.dp || LocalDensity.current.fontScale >= 1.4f
    Column(Modifier.fillMaxSize().then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.zone5_graphics_artboard), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.zone5_graphics_dimensions, document.width, document.height, document.layers.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface((if (short) Modifier.height(220.dp) else Modifier.weight(1f)).fillMaxWidth().heightIn(min = 120.dp), color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            StudioDocumentPreview(service, document, Modifier.fillMaxSize().drawBehind {
                val tile = 14.dp.toPx()
                for (row in 0..(size.height / tile).toInt()) for (column in 0..(size.width / tile).toInt()) {
                    if ((row + column) % 2 == 0) drawRect(checker, Offset(column * tile, row * tile), Size(tile, tile))
                }
            }.padding(16.dp))
        }
        if (document.layers.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            document.layers.asReversed().forEach { layer ->
                FilterChip(selected = layer.id == document.selectedLayerId, onClick = { onSelect(layer.id) }, enabled = !busy,
                    label = { Text(layer.name) }, leadingIcon = {
                        Zone5Icon(when (layer.kind) {
                            StudioLayerKind.IMAGE -> "type_image"; StudioLayerKind.TEXT -> "studio_text"
                            StudioLayerKind.INK -> "studio_draw"; else -> "studio_shapes"
                        }, com.halo.app.ui.icons.HaloGlyph.FILE, Modifier.size(20.dp))
                    }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        selected?.let { layer ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(layer.name + if (layer.locked) " · " + uiText("Locked") else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onEdit(layer) }, enabled = !busy) { Text(stringResource(R.string.studio_edit_layer)) }
            }
        }
        HorizontalDivider()
        WorkspaceContinueButton(project.id, project.revision.toString(), enabled = !busy)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Zone5ActionButton(stringResource(R.string.studio_draw), "studio_draw", !busy, onClick = onDraw)
            Zone5ActionButton(stringResource(R.string.studio_text), "studio_text", !busy, onClick = onText)
            Zone5ActionButton(stringResource(R.string.studio_rectangle), "studio_shapes", !busy, onClick = onShapes)
            Zone5ActionButton(stringResource(R.string.studio_import_image), "action_import", !busy, onClick = onImage)
            Zone5ActionButton(stringResource(R.string.studio_layers), "studio_layers", !busy, onClick = onLayers)
            Zone5ActionButton(stringResource(R.string.studio_transforms), "studio_transform", !busy, onClick = onTransform)
        }
    }
    }
}
