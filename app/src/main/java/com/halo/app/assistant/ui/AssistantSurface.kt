package com.halo.app.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.interaction.InteractionSurfaceRenderer
import com.halo.app.interaction.SurfaceRenderMode
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.sdk.runtime.InteractionTurnState

@Composable
fun AssistantSurface(
    interactionState: InteractionTurnState?,
    screenContextAttached: Boolean,
    onStopClicked: () -> Unit,
    onOpenHaloClicked: () -> Unit,
    interactionSurface: InteractionSurface? = null,
    onInteractionRespond: (InteractionResponse) -> Unit = {},
) {
    val statusText = when (interactionState) {
        is InteractionTurnState.Listening -> "Listening…"
        is InteractionTurnState.Transcribing -> "Understanding…"
        is InteractionTurnState.Preparing -> "Thinking…"
        is InteractionTurnState.Generating -> "Thinking…"
        is InteractionTurnState.Speaking -> "Speaking…"
        is InteractionTurnState.Failed -> "Failed"
        is InteractionTurnState.Cancelled,
        is InteractionTurnState.Interrupted -> "Cancelled"
        is InteractionTurnState.Completed,
        is InteractionTurnState.Idle, null -> "Ready"
    }

    val contextText = if (screenContextAttached) {
        "Screen context attached"
    } else {
        "Screen context not requested"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Halo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            /*
             * RUNTIME-019: the assistant overlay renders the SAME semantic
             * surface as the main app, in COMPACT mode. No separate approval
             * UI. Hiding this overlay never resolves the durable request
             * (spec #35) — the full app still shows it.
             */
            interactionSurface?.let { surface ->
                Spacer(modifier = Modifier.height(12.dp))
                InteractionSurfaceRenderer(
                    surface = surface,
                    onRespond = onInteractionRespond,
                    mode = SurfaceRenderMode.COMPACT,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = contextText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onStopClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
                Button(
                    onClick = onOpenHaloClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Halo")
                }
            }
        }
    }
}
