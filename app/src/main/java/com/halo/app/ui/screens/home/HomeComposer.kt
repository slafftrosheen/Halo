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

/** H02 composer: the same runtime owns Send, Talk, Stop and barge-in. */
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
    val primary = homeComposerPrimaryAction(value, enabled, voiceEnabled, showStop, focusAllowed)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom) {
        TextField(
            value = value, onValueChange = onValueChange,
            enabled = focusAllowed,
            modifier = Modifier.weight(1f).semantics { contentDescription = messageLabel }.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            placeholder = { Text(uiText("Ask Halo…")) }, minLines = 1, maxLines = 4,
            shape = HaloShapes.pill,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (homeComposerPrimaryAction(value, enabled, voiceEnabled, showStop, focusAllowed)
                    == HomeComposerAction.SEND) onSubmit()
            }),
        )
        val label = when (primary) {
            HomeComposerAction.STOP -> uiText("Stop")
            HomeComposerAction.SEND -> sendLabel
            else -> voiceContentDescription
        }
        FilledIconButton(onClick = {
            when (primary) {
                HomeComposerAction.STOP -> onStop()
                HomeComposerAction.SEND -> onSubmit()
                HomeComposerAction.VOICE -> onVoice()
                HomeComposerAction.NONE -> Unit
            }
        }, enabled = primary != HomeComposerAction.NONE,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = label
                if (voiceActive) stateDescription = listeningLabel
            }, shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (showStop) scheme.onSurface else scheme.primaryContainer,
                contentColor = if (showStop) scheme.surface else scheme.onPrimaryContainer)) {
            Icon(painterResource(when (primary) {
                HomeComposerAction.STOP -> R.drawable.halo_ic_stop
                HomeComposerAction.SEND -> R.drawable.halo_ic_send
                else -> R.drawable.halo_ic_mic
            }), null, Modifier.size(24.dp))
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
