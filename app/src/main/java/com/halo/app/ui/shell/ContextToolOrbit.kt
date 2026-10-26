package com.halo.app.ui.shell

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloOrbContextAction
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.pow

/** Scroll position is mapped onto a real upper orbit, including the touch nodes.
 * LazyRow owns click/drag arbitration and bounded snapping; no animation clock or
 * navigation control participates in the orbit. Orb is a separate, fixed sibling.
 */
@Composable
internal fun ContextToolOrbit(
    actions: List<HaloOrbContextAction>,
    onAction: (HaloOrbContextAction) -> Unit,
    orb: @Composable (Dp) -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(184.dp)) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val target = with(density) { 48.dp.toPx() }
            val centerY = with(density) { 112.dp.toPx() }
            val verticalRadius = with(density) { 88.dp.toPx() }
            val slots = if (maxWidth >= 284.dp) 5 else 4
            val slotWidth = maxWidth / slots
            val inset = if (actions.size < slots) slotWidth * (slots - actions.size) / 2 else 0.dp
            key(actions.map { it.id }) {
                val state = rememberLazyListState()
                val centered by remember {
                    derivedStateOf {
                        val midpoint = state.layoutInfo.viewportSize.width / 2
                        state.layoutInfo.visibleItemsInfo.minByOrNull {
                            abs(orbitItemCenter(it.offset, it.size, state.layoutInfo.viewportStartOffset) - midpoint)
                        }?.key
                    }
                }
                LazyRow(
                    state = state,
                    flingBehavior = rememberSnapFlingBehavior(state),
                    modifier = Modifier.fillMaxWidth().height(136.dp).clipToBounds(),
                    contentPadding = PaddingValues(horizontal = inset),
                ) {
                    items(actions, key = { it.id }) { action ->
                        Box(Modifier.width(slotWidth).height(140.dp), contentAlignment = Alignment.TopCenter) {
                            IconButton(
                                onClick = { onAction(action) },
                                enabled = action.enabled,
                                modifier = Modifier.offset {
                                    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == action.id }
                                    val centerX = item?.let {
                                        orbitItemCenter(it.offset, it.size, state.layoutInfo.viewportStartOffset)
                                    } ?: width / 2f
                                    IntOffset(0, orbitItemTop(width, centerX, centerY, verticalRadius, target).roundToInt())
                                }.size(48.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) { HaloIcon(action.glyph, uiText(action.title), Modifier.size(22.dp)) }
                        }
                    }
                }
                Text(
                    uiText(actions.firstOrNull { it.id == centered }?.title ?: actions.firstOrNull()?.title.orEmpty()),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .width((maxWidth - 104.dp).coerceAtLeast(48.dp)).heightIn(min = 20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Placed after the scroll track: the central PTT target wins in its own
            // bounds. Orbit targets are positioned outside those bounds, not over it.
            Box(Modifier.matchParentSize()) {
                CompositionLocalProvider(com.halo.app.ui.presence.LocalShellOrbitalLayout provides true) { orb(96.dp) }
            }
        }
    }
}

/** Lazy item offsets exclude before-content padding; the Orb uses visual viewport coordinates. */
internal fun orbitItemCenter(offset: Int, size: Int, viewportStart: Int): Float =
    offset - viewportStart + size / 2f

internal fun orbitItemTop(width: Float, centerX: Float, centerY: Float, radiusY: Float, target: Float): Float {
    val radiusX = ((width - target) / 2f).coerceAtLeast(1f)
    val normalized = ((centerX - width / 2f) / radiusX).coerceIn(-1f, 1f)
    // A rounded upper orbit keeps square 48dp touch targets outside the central
    // 96dp PTT rectangle even between snap positions, rather than intersecting
    // its corners as a small circular/elliptical arc would.
    val rise = (1f - normalized.pow(4)).coerceAtLeast(0f).pow(.25f)
    return (centerY - radiusY * rise - target / 2f)
        .coerceAtLeast(0f)
}
