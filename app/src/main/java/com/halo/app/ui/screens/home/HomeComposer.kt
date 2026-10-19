package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloShapes

/** Round 2 composer; Talk still enters the existing conversation runtime directly. */
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
    val scheme = MaterialTheme.colorScheme
    val listeningLabel = uiText("Listening…")
    val messageLabel = uiText("Message")
    val sendLabel = uiText("Send")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = messageLabel }.then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                // Keep drafts editable during a response. Only submission is gated.
                placeholder = { Text(uiText("Ask Halo…")) },
                minLines = 1, maxLines = 4, shape = HaloShapes.pill,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedContainerColor = scheme.surfaceContainerLow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSubmit() }),
                trailingIcon = {
                    IconButton(onClick = onSubmit, enabled = enabled && value.isNotBlank(),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = sendLabel }) {
                        Icon(painterResource(R.drawable.halo_ic_send), null, Modifier.size(24.dp))
                    }
                },
            )
            FilledIconButton(onClick = onVoice, enabled = voiceEnabled,
                modifier = Modifier.size(56.dp).semantics {
                    contentDescription = voiceContentDescription
                    if (voiceActive) stateDescription = listeningLabel
                }, shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.primary, contentColor = scheme.onPrimary)) {
                Icon(painterResource(R.drawable.halo_ic_mic), null, Modifier.size(28.dp))
            }
        }
        if (showStop) HaloActionButton(onClick = onStop, modifier = Modifier.fillMaxWidth(),
            style = HaloActionStyle.STOP) {
            Icon(painterResource(R.drawable.halo_ic_stop), null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(uiText("Stop"))
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
