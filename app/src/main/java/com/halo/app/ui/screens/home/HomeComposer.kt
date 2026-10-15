package com.halo.app.ui.screens.home

import com.halo.app.language.uiText
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
    onAttach: (() -> Unit)? = null,
) {
    val listeningLabel = uiText("Listening…")
    val primary = homeComposerPrimaryAction(value, enabled, voiceEnabled, showStop, focusAllowed)
    val action = when {
        showStop -> HomeComposerAction.STOP
        value.isNotBlank() -> HomeComposerAction.SEND
        else -> HomeComposerAction.VOICE
    }
    val actionLabel = when (action) {
        HomeComposerAction.STOP -> uiText("Stop")
        HomeComposerAction.SEND -> uiText("Send")
        else -> voiceContentDescription
    }
    val canAct = when (action) {
        HomeComposerAction.STOP -> true
        HomeComposerAction.SEND -> enabled
        else -> voiceEnabled
    }
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.weight(1f).heightIn(min = 64.dp).then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            placeholder = { Text(uiText("Ask halo anything")) },
            shape = com.halo.app.ui.design.HaloShapes.pill,
            leadingIcon = { onAttach?.let { attach ->
                IconButton(onClick = attach, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.halo_ic_import), contentDescription = uiText("Attach image"),
                        modifier = Modifier.size(24.dp))
                }
            } },
            trailingIcon = {
                if (action != HomeComposerAction.VOICE && voiceEnabled) IconButton(onClick = onVoice,
                    modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.halo_ic_mic), contentDescription = voiceContentDescription,
                        modifier = Modifier.size(24.dp))
                }
            },
            minLines = 1, maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = scheme.surface,
                focusedContainerColor = scheme.surface, unfocusedBorderColor = scheme.outlineVariant),
        )
        val filled = primary == action && action != HomeComposerAction.STOP
        Surface(onClick = { when (action) {
            HomeComposerAction.STOP -> onStop()
            HomeComposerAction.SEND -> onSubmit()
            else -> onVoice()
        } }, enabled = canAct,
            modifier = Modifier.size(72.dp).semantics {
                contentDescription = actionLabel
                if (voiceActive) stateDescription = listeningLabel
            }, shape = androidx.compose.foundation.shape.CircleShape,
            color = if (filled) scheme.primaryContainer else scheme.surface,
            contentColor = if (filled) scheme.onPrimaryContainer else scheme.onSurface,
            border = if (filled) null else BorderStroke(1.dp, scheme.outlineVariant)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(painterResource(when (action) {
                    HomeComposerAction.STOP -> R.drawable.halo_ic_stop
                    HomeComposerAction.SEND -> R.drawable.halo_ic_send
                    else -> R.drawable.halo_ic_mic
                }), contentDescription = null, modifier = Modifier.size(28.dp))
                if (action == HomeComposerAction.STOP) Text(actionLabel, style = MaterialTheme.typography.labelSmall)
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
