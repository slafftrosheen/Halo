package com.halo.app.ui.screens.home

import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun HaloComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    voiceEnabled: Boolean,
    voiceActive: Boolean,
    voiceContentDescription: String,
    showStop: Boolean,
    focusRequester: FocusRequester? = null,
    focusAllowed: Boolean = true,
) {
    val listeningLabel = uiText("Listening…")
    val talkLabel = voiceContentDescription
    val sendLabel = uiText("Send")
    val primary = homeComposerPrimaryAction(value, enabled, voiceEnabled, showStop, focusAllowed)
    val textMeasurer = rememberTextMeasurer()
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                // Keep a draft editable while Halo works; only submission is gated.
                modifier = Modifier.fillMaxWidth().then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                label = { Text(uiText("Message")) },
                placeholder = { Text(uiText("Ask Halo…")) },
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSubmit() }),
            )
            // Adapt to translated labels and system text size, never to interaction state.
            // Keep words intact: lose decorative icons before stacking labelled actions.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val labelWidth = with(density) {
                    maxOf(textMeasurer.measure(talkLabel, MaterialTheme.typography.labelLarge).size.width,
                        textMeasurer.measure(sendLabel, MaterialTheme.typography.labelLarge).size.width).toDp()
                }
                val halfWidth = (maxWidth - 12.dp) / 2
                val stacked = halfWidth < labelWidth + 32.dp
                val showIcons = (if (stacked) maxWidth else halfWidth) >= labelWidth + 60.dp
                val talk: @Composable (Modifier) -> Unit = { target ->
                    HaloActionButton(onClick = onVoice, modifier = target.semantics {
                        contentDescription = voiceContentDescription
                        if (voiceActive) stateDescription = listeningLabel
                    }, enabled = voiceEnabled, style = if (primary == HomeComposerAction.VOICE) HaloActionStyle.PRIMARY else HaloActionStyle.SECONDARY) {
                        if (showIcons) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(talkLabel)
                    }
                }
                val send: @Composable (Modifier) -> Unit = { target ->
                    HaloActionButton(onClick = onSubmit, modifier = target, enabled = enabled && value.isNotBlank(),
                        style = if (primary == HomeComposerAction.SEND) HaloActionStyle.PRIMARY else HaloActionStyle.SECONDARY) {
                        if (showIcons) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(sendLabel)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    talk(Modifier.fillMaxWidth())
                    send(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    talk(Modifier.weight(1f))
                    send(Modifier.weight(1f))
                }
            }
            if (showStop) HaloActionButton(onClick = onStop, modifier = Modifier.fillMaxWidth(),
                style = HaloActionStyle.STOP) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(uiText("Stop"))
            }
        }
    }
}

internal enum class HomeComposerAction { NONE, VOICE, SEND, STOP }

/** Presentation only. Runtime/submission owners continue to admit or reject operations. */
internal fun homeComposerPrimaryAction(draft: String, canSend: Boolean, canSpeak: Boolean,
    showStop: Boolean, focusAllowed: Boolean): HomeComposerAction = when {
    showStop -> HomeComposerAction.STOP
    !focusAllowed -> HomeComposerAction.NONE
    canSend && draft.isNotBlank() -> HomeComposerAction.SEND
    canSpeak && draft.isBlank() -> HomeComposerAction.VOICE
    else -> HomeComposerAction.NONE
}
