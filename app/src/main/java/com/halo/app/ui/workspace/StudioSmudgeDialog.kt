package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioArtPoint
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioLayer
import com.halo.app.data.studio.StudioPixelEdit
import com.halo.app.data.studio.StudioPixelMask
import com.halo.app.data.studio.StudioPixelOperation
import com.halo.app.data.studio.StudioService
import com.halo.app.data.studio.StudioSmudgeStroke
import com.halo.app.data.studio.smudgeStudioPixels
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class StudioSmudgeDraft {
    var strength by mutableIntStateOf(65)
    var radius by mutableIntStateOf(32)
    var wet by mutableStateOf(false)
    var strokes by mutableStateOf<List<StudioSmudgeStroke>>(emptyList())
    var trace by mutableStateOf<List<StudioArtPoint>>(emptyList())
        private set
    val canDraw get() = strength > 0 && strokes.size < 12

    fun input() = StudioArtworkInput(
        StudioArtGesture.DRAG,
        onStart = { point, _ -> trace = listOf(point) },
        onMove = { point -> trace = trace.sampleStudioPoint(point) },
        onEnd = { point ->
            val path = trace.sampleStudioPoint(point)
            if (canDraw && path.size >= 2) strokes = strokes + StudioSmudgeStroke(path, strength, radius, wet)
            trace = emptyList()
        },
        onCancel = { trace = emptyList() },
    )
}

private data class StudioSmudgePreview(
    val strokes: List<StudioSmudgeStroke>, val selection: StudioPixelMask?, val bitmap: Bitmap?, val changed: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioSmudgeDialog(
    service: StudioService, document: StudioDocument, layer: StudioLayer, selection: StudioPixelMask?,
    ready: Boolean, onApply: (StudioPixelEdit.Apply) -> Unit, onDismiss: () -> Unit,
) {
    val draft = remember(document, layer) { StudioSmudgeDraft() }
    val loaded = rememberStudioPixelImage(service, document, layer.id)
    val source = loaded?.bitmap
    val strokes = draft.strokes
    val result by produceState<StudioSmudgePreview?>(null, source, strokes, selection) {
        if (source == null || strokes.isEmpty()) return@produceState
        val edge = maxOf(document.width, document.height)
        value = withContext(Dispatchers.Default) {
            val canvas = source.copy(Bitmap.Config.ARGB_8888, true)
            if (canvas == null) StudioSmudgePreview(strokes, selection, null)
            else try {
                StudioSmudgePreview(strokes, selection, canvas, smudgeStudioPixels(canvas, strokes, selection, edge))
            } catch (cancelled: CancellationException) {
                canvas.recycle()
                throw cancelled
            } catch (_: Exception) {
                canvas.recycle()
                StudioSmudgePreview(strokes, selection, null)
            }
        }
    }
    val matching = result?.takeIf { it.selection === selection }
    val finish = matching?.takeIf { it.strokes == strokes }
    val picture = if (strokes.isEmpty()) source else matching?.bitmap ?: source
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Smudge / blend")) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Drag across the layer to blend its colours. Applying converts this layer to pixels."))
            when {
                loaded?.failed == true || finish != null && finish.bitmap == null ->
                    Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                picture == null -> CircularProgressIndicator()
                else -> StudioPixelPreview(picture, ready && draft.canDraw, draft.input(), selection, draft.trace)
            }
            if (selection != null) Text(uiText("Smudging stays inside the active selection."))
            if (strokes.isNotEmpty() && finish == null) CircularProgressIndicator()
            if (finish != null && finish.bitmap != null && !finish.changed) Text(uiText("The stroke did not change any pixels."))
            Text(uiText("Strength %1\$d%%", draft.strength))
            Slider(draft.strength.toFloat(), { draft.strength = it.roundToInt() }, valueRange = 0f..100f, enabled = ready)
            Text(uiText("Brush radius %1\$d px", draft.radius))
            Slider(draft.radius.toFloat(), { draft.radius = it.roundToInt() }, valueRange = 4f..120f, enabled = ready)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(draft.wet, { draft.wet = !draft.wet }, label = { Text(uiText("Wet mix")) }, enabled = ready)
                TextButton({ draft.strokes = draft.strokes.dropLast(1) }, enabled = ready && strokes.isNotEmpty()) {
                    Text(uiText("Undo stroke"))
                }
            }
            if (strokes.size == 12) Text(uiText("Apply these strokes before adding more."))
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
        }
    }, confirmButton = {
        TextButton(onClick = { onApply(StudioPixelEdit.Apply(StudioPixelOperation.SMUDGE, strokes = strokes)) },
            enabled = ready && strokes.isNotEmpty() && finish?.changed == true) { Text(stringResource(R.string.studio_apply)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
