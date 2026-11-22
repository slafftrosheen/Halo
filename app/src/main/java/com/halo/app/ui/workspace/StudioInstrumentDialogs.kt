package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioCommand
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioLayer
import com.halo.app.data.studio.StudioService
import com.halo.app.data.studio.apply
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun StudioMoveDialog(document: StudioDocument, layer: StudioLayer, ready: Boolean,
    onApply: (StudioCommand) -> Unit, onDismiss: () -> Unit) {
    var x by remember(layer) { mutableStateOf((layer.bounds.x * document.width / 1000f).roundToInt().toString()) }
    var y by remember(layer) { mutableStateOf((layer.bounds.y * document.height / 1000f).roundToInt().toString()) }
    var snap by remember(layer) { mutableStateOf(false) }
    val targetX = x.toIntOrNull()?.takeIf { it in -document.width..(document.width * 2) }
    val targetY = y.toIntOrNull()?.takeIf { it in -document.height..(document.height * 2) }
    fun snapToEdge(position: Int, size: Int): Int = if (!snap) position else
        listOf(0, 1000 - size).firstOrNull { kotlin.math.abs(it - position) <= 15 } ?: position
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Move layer")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioNumberField(stringResource(R.string.studio_x), x, { x = it }, "px", ready)
            StudioNumberField(stringResource(R.string.studio_y), y, { y = it }, "px", ready)
            FilterChip(snap, { snap = !snap }, label = { Text(uiText("Snap to edges")) }, enabled = ready)
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
        }
    }, confirmButton = {
        TextButton(onClick = {
            val bounds = layer.bounds.copy(
                x = snapToEdge((requireNotNull(targetX) * 1000f / document.width).roundToInt(), layer.bounds.width).coerceIn(-1000, 2000),
                y = snapToEdge((requireNotNull(targetY) * 1000f / document.height).roundToInt(), layer.bounds.height).coerceIn(-1000, 2000),
            )
            onApply(StudioCommand.Replace(layer.copy(bounds = bounds)))
        }, enabled = ready && targetX != null && targetY != null) { Text(stringResource(R.string.studio_apply)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
internal fun StudioLightDialog(service: StudioService, document: StudioDocument, layer: StudioLayer, ready: Boolean,
    onApply: (StudioCommand) -> Unit, onDismiss: () -> Unit) {
    var failed by remember(document, layer) { mutableStateOf(false) }
    var strength by remember(layer) { mutableFloatStateOf(0.65f) }
    val newId = remember(layer) { UUID.randomUUID().toString() }
    val layerName = uiText("Light correction")
    val correction by produceState<Int?>(null, service, document, layer) {
        try {
            val standalone = document.copy(background = 0, layers = listOf(layer), selectedLayerId = layer.id)
            val bitmap = service.previewDocument(standalone)
            value = withContext(Dispatchers.Default) {
                try {
                    var luminance = 0.0
                    var samples = 0
                    for (y in 0 until bitmap.height step (bitmap.height / 40).coerceAtLeast(1)) {
                        for (x in 0 until bitmap.width step (bitmap.width / 40).coerceAtLeast(1)) {
                            val pixel = bitmap.getPixel(x, y)
                            if (android.graphics.Color.alpha(pixel) < 200) continue
                            luminance += 0.2126 * android.graphics.Color.red(pixel) +
                                0.7152 * android.graphics.Color.green(pixel) + 0.0722 * android.graphics.Color.blue(pixel)
                            samples++
                        }
                    }
                    val average = if (samples == 0) 128.0 else luminance / samples
                    ((128.0 - average) * 3).roundToInt().coerceIn(-260, 260)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    val change = correction?.let { estimate ->
        val copy = layer.copy(id = newId, name = layerName, locked = false,
            brightness = (layer.brightness + estimate * strength).roundToInt().coerceIn(-1000, 1000),
            contrast = (layer.contrast + 160 * strength).roundToInt().coerceIn(0, 3000),
            saturation = (layer.saturation + 80 * strength).roundToInt().coerceIn(0, 3000))
        StudioCommand.Batch(listOf(StudioCommand.Add(copy), StudioCommand.Move(copy.id, document.layers.indexOf(layer) + 1)))
    }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Fix the light")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                failed -> Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                change == null -> CircularProgressIndicator()
                else -> StudioDocumentPreview(service, document.apply(change), Modifier.fillMaxWidth().height(160.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Strength"))
                Text("${(strength * 100).roundToInt()}%")
            }
            Slider(strength, { strength = it }, enabled = ready && correction != null)
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
        }
    }, confirmButton = {
        TextButton(onClick = { change?.let(onApply) }, enabled = ready && change != null && strength > 0f) {
            Text(stringResource(R.string.studio_apply))
        }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
