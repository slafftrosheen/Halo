package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioFillRequest
import com.halo.app.data.studio.StudioFillStyle
import com.halo.app.data.studio.StudioService
import com.halo.app.data.studio.renderStudioFill
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioFillDialog(service: StudioService, document: StudioDocument, startColor: Int, ready: Boolean,
    onApply: (StudioFillRequest) -> Unit, onDismiss: () -> Unit) {
    var point by remember(document) { mutableStateOf<Offset?>(null) }
    var tolerance by remember(document) { mutableIntStateOf(12) }
    var style by remember(document) { mutableStateOf(StudioFillStyle.SOLID) }
    var color by remember(startColor) { mutableStateOf("#" + startColor.toUInt().toString(16).padStart(8, '0')) }
    var end by remember { mutableStateOf("#FFFFFF") }
    var failed by remember(document) { mutableStateOf(false) }
    val base by produceState<Bitmap?>(null, document) {
        try { value = service.fillBase(document, preview = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val firstColor = parseStudioFillColour(color)
    val secondColor = if (style == StudioFillStyle.SOLID) firstColor else parseStudioFillColour(end)
    val request = point?.let { anchor ->
        if (firstColor != null && secondColor != null) StudioFillRequest(anchor.x, anchor.y, tolerance, firstColor, secondColor, style) else null
    }
    val loadedBase = base
    val overlay by produceState<Pair<StudioFillRequest, Bitmap>?>(null, loadedBase, request) {
        val active = request
        val source = loadedBase
        if (source == null || active == null) { value = null; return@produceState }
        delay(100)
        try { value = active to renderStudioFill(source, active) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Fill")) }, text = {
        Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Tap the area to fill."))
            when {
                failed -> Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                loadedBase == null -> CircularProgressIndicator()
                else -> Box(Modifier.fillMaxWidth().heightIn(max = 250.dp).aspectRatio(loadedBase.width.toFloat() / loadedBase.height)
                    .pointerInput(loadedBase, ready) {
                        if (ready) detectTapGestures { touch ->
                            point = Offset((touch.x / size.width).coerceIn(0f, 1f), (touch.y / size.height).coerceIn(0f, 1f))
                        }
                    }) {
                    Image(loadedBase.asImageBitmap(), uiText("Artboard fill preview"), Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    overlay?.takeIf { it.first == request }?.second?.let {
                        Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioFillStyle.entries.forEach { option ->
                    FilterChip(style == option, { style = option }, label = { Text(uiText(option.label)) })
                }
            }
            Text(uiText("Tolerance %1\$d%%", tolerance))
            Slider(tolerance.toFloat(), { tolerance = it.roundToInt() }, valueRange = 0f..100f)
            OutlinedTextField(color, { color = it.take(9) }, singleLine = true, isError = firstColor == null,
                label = { Text(uiText("Fill colour (HEX)")) })
            if (style != StudioFillStyle.SOLID) OutlinedTextField(end, { end = it.take(9) }, singleLine = true, isError = secondColor == null,
                label = { Text(uiText("End colour (HEX)")) })
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
        }
    }, confirmButton = {
        TextButton(onClick = { request?.let(onApply) }, enabled = ready && request != null && overlay?.first == request) {
            Text(uiText("Fill on new layer"))
        }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

private fun parseStudioFillColour(value: String): Int? = value.trim().removePrefix("#").let { hex ->
    if (hex.length != 6 && hex.length != 8) null
    else hex.toUIntOrNull(16)?.let { if (hex.length == 6) (it or 0xff000000u).toInt() else it.toInt() }
}
