package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    onAttach: (() -> Unit)? = null,
    onTools: (() -> Unit)? = null,
    toolsMenu: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val listeningLabel = uiText("Listening…")
    val messageLabel = uiText("Message")
    val sendLabel = uiText("Send")
    val primary = homeComposerPrimaryAction(value, enabled, voiceEnabled, showStop, focusAllowed)
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        TextField(
            value = value, onValueChange = onValueChange,
            enabled = focusAllowed,
            modifier = fieldModifier.semantics { contentDescription = messageLabel }.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            placeholder = { Text(uiText("Ask Halo…")) }, minLines = 1, maxLines = if (compact) 2 else 4,
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
    }
    val actions: @Composable RowScope.() -> Unit = {
        onAttach?.let { action ->
            IconButton(onClick = action, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.halo_ic_add), uiText("Attach"), Modifier.size(24.dp))
            }
        }
        onTools?.let { action ->
            Box {
                if (!compact && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.5f) {
                    TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) {
                        HaloIcon(HaloGlyph.TOOL, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("Tools"), maxLines = 1)
                    }
                } else IconButton(onClick = action, modifier = Modifier.size(48.dp)) {
                    HaloIcon(HaloGlyph.TOOL, uiText("Tools"))
                }
                toolsMenu?.invoke()
            }
        }
        if (!compact) Spacer(Modifier.weight(1f))
        val label = when (primary) {
            HomeComposerAction.STOP -> uiText("Stop")
            HomeComposerAction.SEND -> sendLabel
            else -> if (value.isNotBlank()) sendLabel else voiceContentDescription
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
                else -> if (value.isNotBlank()) R.drawable.halo_ic_send else R.drawable.halo_ic_mic
            }), null, Modifier.size(24.dp))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Keep the field at the same composition position when the IME changes window height.
            field(Modifier.weight(1f))
            if (compact) actions()
        }
        if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
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
