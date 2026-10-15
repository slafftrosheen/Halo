package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import kotlinx.coroutines.delay

/** Opening the real microphone starts one Summon; a restored open mic does not. */
internal fun shouldSummon(previousCapture: Boolean, currentCapture: Boolean): Boolean =
    !previousCapture && currentCapture

@Composable
internal fun HomeVoiceSummonFx(micOpen: Boolean) {
    var previous by remember { mutableStateOf(micOpen) }
    var eventId by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(micOpen) {
        val opened = shouldSummon(previous, micOpen)
        previous = micOpen
        if (opened) {
            eventId++
            visible = true
            // Supplied clip: 24 frames @ 30 FPS. Leave loading headroom.
            delay(1200L)
            visible = false
        } else if (!micOpen) {
            visible = false
        }
    }
    if (visible) key(eventId) {
        HaloFxAnimation(HaloFxMoment.SUMMON, modifier = Modifier.size(112.dp))
    }
}
