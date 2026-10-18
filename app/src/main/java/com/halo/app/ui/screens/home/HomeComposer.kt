package com.halo.app.ui.screens.home

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.halo.app.R
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
            HaloTextField(
                value = value, onValueChange = onValueChange,
                // Keep a draft editable while Halo works; only submission is gated.
                modifier = Modifier.fillMaxWidth().then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                label = { Text(uiText("Message")) },
                placeholder = { Text(uiText("Ask Halo…")) },
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSubmit() }),
                voiceEnabled = enabled, kind = VoiceFieldKind.TEXT)
            HaloActionButton(onClick = onSubmit, modifier = Modifier.fillMaxWidth(),
                enabled = enabled && value.isNotBlank(), style = HaloActionStyle.PRIMARY) {
                Icon(painterResource(R.drawable.halo_ic_send), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(sendLabel)
            }
            if (showStop) HaloActionButton(onClick = onStop, modifier = Modifier.fillMaxWidth(),
                style = HaloActionStyle.STOP) {
                Icon(painterResource(R.drawable.halo_ic_stop), contentDescription = null, modifier = Modifier.size(24.dp))
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
