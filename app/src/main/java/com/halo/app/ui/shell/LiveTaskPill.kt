package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.*
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
    val moment = when { showStop -> HaloPackageMoment.STOP; needsYou -> HaloPackageMoment.APPROVAL; else -> HaloPackageMoment.STEP_RUNNING }
    Surface(onClick = onReview, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            key(moment, if (showStop) epoch else 0L) { HaloPackageFx(moment, Modifier.size(32.dp)) }
            Text(uiText(when {
                showStop -> "Stopped"
                needsYou -> "Waiting for you"
                session is CognitiveSessionState.ExecutingTool -> "Working on your request"
                else -> "Thinking it through"
            }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = if (needsYou) Color(0xFFFF9F6B) else MaterialTheme.colorScheme.onSurface)
            if (running && !showStop) TextButton(onClick = onStop) { Text(uiText("Stop")) }
            else if (needsYou) TextButton(onClick = onReview) { Text(uiText("Review")) }
        }
    }
}
