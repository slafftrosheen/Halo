package com.halo.app.ui.shell

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.zone5.LabelledSatellite5

/** Manual, bounded three-slot orbit. Labels and their targets share measured bounds.
 * LazyRow consumes drags before a child click; no clock, scrolling callback or animation invokes work.
 */
@Composable
internal fun ContextToolOrbit(
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    orb: @Composable (Dp) -> Unit, orbSize: Dp = 72.dp,
    ownerKey: String? = null,
    minimumHeight: Dp = 180.dp,
) {
    val state = rememberSaveable(ownerKey, saver = LazyListState.Saver) { LazyListState() }
    Column(Modifier.fillMaxWidth().heightIn(min = minimumHeight), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (actions.isNotEmpty()) BoxWithConstraints(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            val slotWidth = (maxWidth - 16.dp) / 3
            val inset = if (actions.size < 3) (slotWidth + 8.dp) * (3 - actions.size) / 2 else 0.dp
            LazyRow(state = state, flingBehavior = rememberSnapFlingBehavior(state),
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = inset)) {
                itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
                    LabelledSatellite5(action, { onAction(action) },
                        Modifier.width(slotWidth).padding(top = if (index % 3 == 1) 0.dp else 12.dp))
                }
            }
        }
        orb(orbSize)
    }
}
