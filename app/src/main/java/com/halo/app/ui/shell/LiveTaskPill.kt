package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.HaloMotionEvents
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.screens.home.HomeUiState
import com.halo.sdk.provider.CognitiveSessionState

/** Navigation follows the task; the task keeps its existing runtime owner. */
@Composable
internal fun LiveTaskPill(state: HomeUiState, onReview: () -> Unit, onStop: () -> Unit) {
    val session = state.sessionState
    val running = session != CognitiveSessionState.Idle &&
        session !is CognitiveSessionState.Completed && session !is CognitiveSessionState.Failed
    val needsYou = state.runtimeWaitingCount > 0
    val epoch by HaloMotionEvents.stopEpoch.collectAsState()
    var observedEpoch by remember { mutableLongStateOf(epoch) }
    var showStop by remember { mutableStateOf(false) }
    LaunchedEffect(epoch) {
        if (epoch != observedEpoch) {
            observedEpoch = epoch
            showStop = true
            kotlinx.coroutines.delay(600)
            showStop = false
        }
    }
    if (!running && !needsYou && !showStop) return
    val glyph = when { showStop -> HaloGlyph.STOP; needsYou -> HaloGlyph.APPROVAL; else -> HaloGlyph.ACTION }
    Surface(onClick = onReview, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The shell Orb owns animated presence. A task awaiting review can stay
            // here indefinitely, so its status icon must not run a second frame clock.
            HaloIcon(glyph, null, Modifier.size(24.dp))
            Text(uiText(when {
                showStop -> "Stopped"
                needsYou -> "Waiting for you"
                session is CognitiveSessionState.ExecutingTool -> "Working on your request"
                else -> "Thinking it through"
            }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
            if (running && !showStop) TextButton(onClick = onStop) { Text(uiText("Stop")) }
            else if (needsYou) TextButton(onClick = onReview) { Text(uiText("Review")) }
        }
    }
}
