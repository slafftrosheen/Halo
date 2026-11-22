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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioArtPoint
import com.halo.app.data.studio.StudioDocument
import com.halo.app.data.studio.StudioPixelEdit
import com.halo.app.data.studio.StudioPixelMask
import com.halo.app.data.studio.StudioSelectionCombine
import com.halo.app.data.studio.StudioSelectionRequest
import com.halo.app.data.studio.StudioService
import com.halo.app.data.studio.studioPixelMask
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

internal enum class StudioPixelTool(val instrumentId: String, val label: String) {
    MARQUEE("studio.rectangle_ellipse_select", "Rectangle / ellipse select"),
    LASSO("studio.lasso", "Lasso"),
    WAND("studio.magic_wand", "Magic wand"),
    SUBJECT("studio.select_subject", "Select subject"),
    SMUDGE("studio.smudge_blend", "Smudge / blend"),
}

private class StudioSelectionDraft(val tool: StudioPixelTool) {
    var ellipse by mutableStateOf(false)
    var polygon by mutableStateOf(false)
    var contiguous by mutableStateOf(true)
    var sampleAll by mutableStateOf(tool != StudioPixelTool.WAND)
    var feather by mutableIntStateOf(0)
    var tolerance by mutableIntStateOf(12)
    var refine by mutableIntStateOf(3)
    private var combine by mutableStateOf(StudioSelectionCombine.REPLACE)
    var trace by mutableStateOf<List<StudioArtPoint>>(emptyList())
        private set
    private var completed by mutableStateOf<List<StudioArtPoint>>(emptyList())
    private var seed by mutableStateOf<StudioArtPoint?>(null)
    private var gestureCombine: StudioSelectionCombine? = null
    private var finishedCombine by mutableStateOf<StudioSelectionCombine?>(null)

    fun input(): StudioArtworkInput {
        val tap = tool == StudioPixelTool.WAND || tool == StudioPixelTool.SUBJECT || polygon
        return StudioArtworkInput(if (tap) StudioArtGesture.TAP else StudioArtGesture.DRAG,
            onStart = { point, modifier ->
                when {
                    tool == StudioPixelTool.WAND || tool == StudioPixelTool.SUBJECT -> {
                        seed = point
                        finishedCombine = modifier
                    }
                    polygon -> {
                        if (trace.isEmpty()) gestureCombine = modifier
                        val first = trace.firstOrNull()
                        if (trace.size >= 3 && first != null && hypot(first.x - point.x, first.y - point.y) < .032f)
                            closePolygon()
                        else if (trace.size < 384) trace = trace.sampleStudioPoint(point)
                    }
                    else -> {
                        trace = listOf(point)
                        gestureCombine = modifier
                    }
                }
            },
            onMove = ::append,
            onEnd = { point ->
                append(point)
                if (tool == StudioPixelTool.MARQUEE && trace.size >= 2 || tool == StudioPixelTool.LASSO && trace.size >= 3)
                    closePolygon()
                else trace = emptyList()
            },
            onCancel = { trace = emptyList() })
    }

    private fun append(point: StudioArtPoint) {
        val first = trace.firstOrNull() ?: return
        if (tool == StudioPixelTool.MARQUEE) trace = listOf(first, point)
        else trace = trace.sampleStudioPoint(point)
    }

    fun closePolygon() {
        completed = trace
        finishedCombine = gestureCombine
        trace = emptyList()
    }

    fun changeLasso(polygon: Boolean) {
        trace = emptyList()
        this.polygon = polygon
    }

    fun request(layerId: String?): StudioPixelEdit.Select? {
        val shape = when (tool) {
            StudioPixelTool.MARQUEE -> if (completed.size == 2) StudioSelectionRequest.Marquee(completed[0], completed[1], ellipse) else null
            StudioPixelTool.LASSO -> if (completed.size >= 3) StudioSelectionRequest.Lasso(completed) else null
            StudioPixelTool.WAND -> seed?.let { StudioSelectionRequest.Wand(it, tolerance, contiguous) }
            StudioPixelTool.SUBJECT -> StudioSelectionRequest.Subject(refine, seed)
            StudioPixelTool.SMUDGE -> null
        } ?: return null
        return StudioPixelEdit.Select(shape, feather, finishedCombine ?: combine, sampleAll || layerId == null)
    }

    fun chooseCombine(mode: StudioSelectionCombine) {
        combine = mode
        finishedCombine = null
    }

    val effectiveCombine get() = finishedCombine ?: combine
}

private data class StudioMaskPreview(
    val source: Bitmap, val before: StudioPixelMask?, val request: StudioPixelEdit.Select, val mask: StudioPixelMask?,
)

@Composable
internal fun StudioSelectionDialog(
    service: StudioService, document: StudioDocument, tool: StudioPixelTool, before: StudioPixelMask?,
    ready: Boolean, onApply: (StudioPixelEdit.Select) -> Unit, onDismiss: () -> Unit,
) {
    val draft = remember(document, tool) { StudioSelectionDraft(tool) }
    val selected = document.layers.firstOrNull { it.id == document.selectedLayerId && it.visible && it.opacity > 0 }
    val layerId = selected?.id
    val imageLayer = if (draft.sampleAll) null else layerId
    val loaded = rememberStudioPixelImage(service, document, imageLayer)
    val bitmap = loaded?.bitmap
    val request = draft.request(layerId)
    val computed by produceState<StudioMaskPreview?>(null, bitmap, request, before) {
        val image = bitmap ?: return@produceState
        val selection = request ?: return@produceState
        delay(70)
        value = withContext(Dispatchers.Default) {
            val mask = studioPixelMask(image, selection.shape, selection.feather, maxOf(document.width, document.height))
            StudioMaskPreview(image, before, selection, mask?.combined(before, selection.combine))
        }
    }
    val measured = computed?.takeIf { it.source === bitmap && it.before === before && it.request == request }
    val progress = bitmap != null && request != null && measured == null
    val useMask = measured?.mask
    val canKeep = useMask != null && (useMask.hasSelection || request?.combine == StudioSelectionCombine.SUBTRACT && before != null)
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(tool.label)) }, text = {
        Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText(selectionInstruction(tool, draft.polygon)))
            when {
                loaded?.failed == true -> Text(stringResource(R.string.studio_preview_error), color = MaterialTheme.colorScheme.error)
                bitmap == null -> CircularProgressIndicator()
                else -> StudioPixelPreview(bitmap, ready, draft.input(), useMask ?: before, draft.trace, draft.ellipse,
                    marquee = tool == StudioPixelTool.MARQUEE)
            }
            if (progress && tool == StudioPixelTool.SUBJECT) CircularProgressIndicator(color = Color(0xff62bb9b))
            if (measured != null && useMask == null) Text(uiText("Subject edges are unclear. Tap the subject or draw a selection."),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ready) Text(stringResource(R.string.zone5_document_changed))
            StudioSelectionOptions(draft, ready, layerId != null, before != null)
        }
    }, confirmButton = {
        TextButton(onClick = { request?.let(onApply) }, enabled = ready && canKeep) { Text(uiText("Keep selection")) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

private fun selectionInstruction(tool: StudioPixelTool, polygon: Boolean): String = when (tool) {
    StudioPixelTool.MARQUEE -> "Drag across the area you want to select."
    StudioPixelTool.LASSO -> if (polygon) "Tap each corner, then tap the first corner or close the shape."
        else "Draw a loop around the area you want to select."
    StudioPixelTool.WAND -> "Tap a colour to select similar pixels."
    StudioPixelTool.SUBJECT -> "Halo looks for the main subject on this phone. Tap the subject to help refine the selection."
    StudioPixelTool.SMUDGE -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioSelectionOptions(draft: StudioSelectionDraft, ready: Boolean, hasLayer: Boolean, hasPrevious: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (draft.tool) {
            StudioPixelTool.MARQUEE -> {
                FilterChip(!draft.ellipse, { draft.ellipse = false }, label = { Text(stringResource(R.string.studio_rectangle)) }, enabled = ready)
                FilterChip(draft.ellipse, { draft.ellipse = true }, label = { Text(stringResource(R.string.studio_ellipse)) }, enabled = ready)
            }
            StudioPixelTool.LASSO -> {
                FilterChip(!draft.polygon, { draft.changeLasso(false) }, label = { Text(uiText("Freehand")) }, enabled = ready)
                FilterChip(draft.polygon, { draft.changeLasso(true) }, label = { Text(uiText("Polygon")) }, enabled = ready)
            }
            StudioPixelTool.WAND -> {
                FilterChip(draft.contiguous, { draft.contiguous = !draft.contiguous }, label = { Text(uiText("Connected pixels")) }, enabled = ready)
                FilterChip(draft.sampleAll || !hasLayer, { draft.sampleAll = !draft.sampleAll },
                    label = { Text(uiText("Sample all layers")) }, enabled = ready && hasLayer)
            }
            else -> Unit
        }
    }
    if (draft.tool == StudioPixelTool.LASSO && draft.polygon) {
        TextButton(draft::closePolygon, enabled = ready && draft.trace.size >= 3) { Text(uiText("Close shape")) }
    }
    if (hasPrevious) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((mode, label) in listOf(
            StudioSelectionCombine.REPLACE to "Replace selection", StudioSelectionCombine.ADD to "Add to selection",
            StudioSelectionCombine.SUBTRACT to "Subtract from selection",
        )) {
            FilterChip(draft.effectiveCombine == mode, { draft.chooseCombine(mode) }, label = { Text(uiText(label)) }, enabled = ready)
        }
    }
    when (draft.tool) {
        StudioPixelTool.MARQUEE, StudioPixelTool.LASSO -> {
            Text(uiText("Feather %1\$d px", draft.feather))
            Slider(draft.feather.toFloat(), { draft.feather = it.roundToInt() }, valueRange = 0f..200f, enabled = ready)
        }
        StudioPixelTool.WAND -> {
            Text(uiText("Tolerance %1\$d%%", draft.tolerance))
            Slider(draft.tolerance.toFloat(), { draft.tolerance = it.roundToInt() }, valueRange = 0f..100f, enabled = ready)
        }
        StudioPixelTool.SUBJECT -> {
            Text(uiText("Edge refine %1\$d", draft.refine))
            Slider(draft.refine.toFloat(), { draft.refine = it.roundToInt() }, valueRange = 0f..10f, steps = 9, enabled = ready)
        }
        else -> Unit
    }
}

internal data class StudioPixelImage(val document: StudioDocument, val layerId: String?, val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
internal fun rememberStudioPixelImage(service: StudioService, document: StudioDocument, layerId: String?): StudioPixelImage? {
    val image by produceState<StudioPixelImage?>(null, service, document, layerId) {
        try { value = StudioPixelImage(document, layerId, service.pixelArtboard(document, layerId, preview = true)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = StudioPixelImage(document, layerId, failed = true) }
    }
    return image?.takeIf { it.layerId == layerId && it.document == document }
}
