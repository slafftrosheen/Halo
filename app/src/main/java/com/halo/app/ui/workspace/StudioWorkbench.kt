package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.*
import com.halo.app.ui.zone6.BindHaloGestureAlternative
import com.halo.app.ui.zone6.HaloGesture
import com.halo.app.ui.zone6.LocalHaloPageGuidance
import com.halo.app.ui.zone6.haloGestureAnchor
import kotlinx.coroutines.flow.collect

/** The artboard owns the working area; manual tools and the current layer have stable positions. */
@Composable
internal fun StudioWorkbench(service: StudioService, project: StudioProject, busy: Boolean,
    onDraw: () -> Unit, onText: () -> Unit, onShapes: () -> Unit, onImage: () -> Unit,
    onLayers: () -> Unit, onTransform: () -> Unit, onSelect: (String) -> Unit, onEdit: (StudioLayer) -> Unit,
    modifier: Modifier = Modifier, appearance: StudioAppearance = StudioAppearance(), onUndo: () -> Unit = {},
    pixelSelection: StudioPixelMask? = null,
    reference: @Composable BoxScope.() -> Unit = {}) {
    val document = project.session.current
    val selected = document.layers.firstOrNull { it.id == document.selectedLayerId }
    val easy = com.halo.app.ui.theme.rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
    if (com.halo.app.ui.presence.LocalShellHaloOrb.current) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (easy && appearance.leftHanded) StudioEasyActions(busy, project.session.undo.isNotEmpty(), onDraw, onText, onImage, onUndo)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val ratio = document.width.toFloat() / document.height
                val width = minOf(maxWidth, maxHeight * ratio)
                StudioArtworkFrame(service, project, appearance, Modifier.width(width).height(width / ratio), pixelSelection, reference)
            }
            if (easy && !appearance.leftHanded) StudioEasyActions(busy, project.session.undo.isNotEmpty(), onDraw, onText, onImage, onUndo)
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
        StudioArtworkFrame(service, project, appearance,
            (if (short) Modifier.height(220.dp) else Modifier.weight(1f)).fillMaxWidth().heightIn(min = 120.dp), pixelSelection, reference)
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

@Composable
private fun StudioArtworkFrame(
    service: StudioService, project: StudioProject, appearance: StudioAppearance, modifier: Modifier,
    pixelSelection: StudioPixelMask?,
    reference: @Composable BoxScope.() -> Unit,
) {
    val guidance = LocalHaloPageGuidance.current
    val visual = com.halo.app.ui.theme.rememberVisualPreferences()
    val reduced by com.halo.app.ui.theme.rememberReducedMotion()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
    var zoom by remember(project.id) { mutableFloatStateOf(1f) }
    var pan by remember(project.id) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { ratio, movement, _ ->
        zoom = (zoom * ratio).coerceIn(1f, 4f)
        pan = if (zoom == 1f) Offset.Zero else pan + movement
    }
    LaunchedEffect(transform, guidance, appearance.snapZoom) {
        var moving = false
        snapshotFlow { transform.isTransformInProgress }.collect { active ->
            if (active && !moving) guidance?.gestureUsed(HaloGesture.STUDIO_MOVE)
            if (!active && moving && appearance.snapZoom && zoom < 1.12f) {
                zoom = 1f
                pan = Offset.Zero
            }
            moving = active
        }
    }
    BindHaloGestureAlternative(HaloGesture.STUDIO_MOVE) {
        if (zoom == 1f) zoom = 1.5f else {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    val shownZoom by animateFloatAsState(zoom, animationSpec = if (transform.isTransformInProgress || reduced)
        snap() else spring(dampingRatio = visual.motionLevel.damping), label = "Studio view")
    Surface(modifier.haloGestureAnchor(HaloGesture.STUDIO_MOVE),
        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxSize().clipToBounds().transformable(transform)) {
            val document = project.session.current
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = shownZoom
                scaleY = shownZoom
                val horizontalLimit = size.width * (shownZoom - 1f).coerceAtLeast(0f) / 2f
                val verticalLimit = size.height * (shownZoom - 1f).coerceAtLeast(0f) / 2f
                translationX = pan.x.coerceIn(-horizontalLimit, horizontalLimit)
                translationY = pan.y.coerceIn(-verticalLimit, verticalLimit)
            }) {
                StudioDocumentPreview(service, document, Modifier.matchParentSize()
                    .drawWithContent { drawContent(); drawStudioPaper(appearance.paper, ink) })
                if (pixelSelection != null) StudioSelectionOverlay(pixelSelection, Modifier.matchParentSize(), document.recipe)
            }
            if (zoom != 1f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero },
                modifier = Modifier.align(if (appearance.leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                    .heightIn(min = 48.dp)) {
                Text(uiText("Fit image"))
            }
            reference()
        }
    }
}

@Composable
private fun StudioEasyActions(
    busy: Boolean, canUndo: Boolean, onDraw: () -> Unit, onText: () -> Unit, onImage: () -> Unit, onUndo: () -> Unit,
) {
    val actions = listOf(R.string.studio_draw to onDraw, R.string.alive_studio_words to onText,
        R.string.alive_studio_picture to onImage, R.string.studio_undo to onUndo)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (label, action) ->
            OutlinedButton(onClick = action, enabled = !busy && (label != R.string.studio_undo || canUndo),
                modifier = Modifier.defaultMinSize(minWidth = 84.dp, minHeight = 84.dp)) {
                Text(stringResource(label))
            }
        }
    }
}
