package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import com.halo.sdk.provider.AiExecutionLocation
import kotlinx.coroutines.delay

/**
 * Provider ID comes from the actual CognitiveSessionState.Reasoning, not from
 * a configured or selected provider. Unknown and LAN providers never light
 * the cloud FX, and no movement is interpreted as an approval or receipt.
 */
internal fun cloudHandoffProviderId(
    reasoningProviderId: String?,
    knownLocations: Map<String, AiExecutionLocation>,
): String? = reasoningProviderId?.takeIf {
    knownLocations[it] == AiExecutionLocation.REMOTE
}

/** One-shot, non-interactive handoff moment; does not replay on entering Home
 * in the middle of an already running turn.
 */
@Composable
internal fun HomeCloudHandoffFx(providerId: String?) {
    var previous by remember { mutableStateOf(providerId) }
    var event by remember { mutableIntStateOf(0) }
    LaunchedEffect(providerId) {
        val newHandoff = providerId != null && previous != providerId
        previous = providerId
        if (newHandoff) event++
    }
    if (event > 0) {
        var visible by remember(event) { mutableStateOf(true) }
        LaunchedEffect(event) {
            delay(2000L) // supplied clip: 60 frames / 30 FPS
            visible = false
        }
        if (visible) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloFxAnimation(HaloFxMoment.CLOUD_HANDOFF,
                    modifier = Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(uiText("Cloud handoff"), style = MaterialTheme.typography.titleSmall)
                    Text(uiText("Using a remote AI provider"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
