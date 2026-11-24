package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.Zone5Icon

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
    surfaceSwitcher: (@Composable () -> Unit)? = null,
    draftIdentity: String? = null,
    draftRevision: Long? = null,
    readAcknowledgedDraft: (() -> ComposerDraftState?)? = null,
    dictation: (@Composable () -> Unit)? = null,
) {
    // The relocated child may recompose before its page's captured text updates.
    // Read text and revision together from the same canonical snapshot; accepting
    // an old literal under a newer revision would block the later clear/recovery.
    val canonicalDraft = readAcknowledgedDraft?.invoke()
    val renderedText = canonicalDraft?.text ?: value
    val renderedRevision = canonicalDraft?.revision ?: draftRevision
    val editor = remember(draftIdentity) { HomeComposerEditBuffer(renderedText, renderedRevision) }
    editor.reconcile(renderedText, renderedRevision)
    val visibleText = editor.value.text
    val scheme = MaterialTheme.colorScheme
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val listeningLabel = uiText("Listening…")
    val messageLabel = uiText("Message")
    val sendLabel = uiText("Send")
    var fieldFocused by remember { mutableStateOf(false) }
    val primary = homeComposerPrimaryAction(visibleText, enabled, voiceEnabled, showStop, focusAllowed, shellOwnsOrb)
    // At large text the four touch targets need their own row, leaving the field full width.
    // Keep the established compact landscape presentation for standalone hosts.
    val compact = if (shellOwnsOrb) androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.4f
        else androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        BasicTextField(
            value = editor.value, onValueChange = { next ->
                // Echo the entire IME value synchronously before the collected page/shell round trip.
                val textChanged = next.text != editor.value.text
                editor.edit(next)
                if (textChanged) {
                    onValueChange(next.text)
                    editor.acknowledge(readAcknowledgedDraft?.invoke())
                }
            },
            enabled = focusAllowed,
            modifier = fieldModifier.onFocusChanged { fieldFocused = it.isFocused }.semantics { contentDescription = messageLabel }.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            // Keep one vertical text scroller across IME/rotation changes. Material's
            // multiline decoration padding clips the baseline in a short landscape row.
            minLines = 1, maxLines = if (compact) 2 else 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
            decorationBox = { editor ->
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = if (compact) 0.dp else 8.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (visibleText.isEmpty()) Text(uiText("Ask Halo…"),
                        style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().testTag("home_composer_editor")) { editor() }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (homeComposerPrimaryAction(visibleText, enabled, voiceEnabled, showStop, focusAllowed, shellOwnsOrb)
                    == HomeComposerAction.SEND) onSubmit()
            }),
        )
    }
    val attachButton: @Composable () -> Unit = {
        onAttach?.let { attach ->
            OutlinedIconButton(onClick = attach, modifier = Modifier.size(48.dp), shape = CircleShape,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = scheme.onSurface.copy(alpha = .07f),
                    contentColor = scheme.onSurface)) {
                Zone5Icon("action_attach", HaloGlyph.FILE, Modifier.size(20.dp), contentDescription = uiText("Attach"))
            }
        }
    }
    var showSurfaceSwitcher by remember { mutableStateOf(false) }
    val actions: @Composable RowScope.() -> Unit = {
        surfaceSwitcher?.takeUnless { shellOwnsOrb }?.let { switcher ->
            Box {
                IconButton(onClick = { showSurfaceSwitcher = true }, modifier = Modifier.size(48.dp)) {
                    HaloIcon(HaloGlyph.HOME, stringResource(R.string.home_chat_screen_switch))
                }
                DropdownMenu(expanded = showSurfaceSwitcher, onDismissRequest = { showSurfaceSwitcher = false }) {
                    Box(Modifier.padding(8.dp)) { switcher() }
                }
            }
        }

        if (!shellOwnsOrb || !compact) attachButton()
        onTools?.takeUnless { shellOwnsOrb }?.let { action ->
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
        dictation?.invoke()
        val label = when (primary) {
            HomeComposerAction.STOP -> uiText("Stop")
            HomeComposerAction.SEND -> sendLabel
            else -> if (shellOwnsOrb || visibleText.isNotBlank()) sendLabel else voiceContentDescription
        }
        if (!shellOwnsOrb || compact || visibleText.isNotBlank() || showStop) FilledIconButton(onClick = {
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
                contentColor = if (showStop) scheme.surface else scheme.onPrimaryContainer,
                disabledContainerColor = scheme.onSurface.copy(alpha = .07f),
                disabledContentColor = scheme.onSurfaceVariant)) {
            when {
                primary == HomeComposerAction.STOP -> HaloIcon(HaloGlyph.STOP, null, Modifier.size(22.dp))
                shellOwnsOrb || visibleText.isNotBlank() -> Zone5Icon("action_send", HaloGlyph.ACTION, Modifier.size(20.dp))
                else -> HaloIcon(HaloGlyph.LISTEN, null, Modifier.size(22.dp))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (shellOwnsOrb) {
                if (compact) attachButton()
                Surface(modifier = Modifier.weight(1f), shape = CircleShape,
                    color = scheme.onSurface.copy(alpha = .07f), contentColor = scheme.onSurface,
                    border = BorderStroke(1.dp, if (fieldFocused) scheme.primary else scheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(end = if (compact) 2.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // Keep one editor instance when Android opens and closes the keyboard.
                        field(Modifier.weight(1f))
                        if (compact) actions()
                    }
                }
            } else {
                field(Modifier.weight(1f))
                if (compact) actions()
            }
        }
        if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
    }
}

internal enum class HomeComposerAction { NONE, VOICE, SEND, STOP }

/** Presentation only. Runtime/submission owners continue to admit or reject operations. */
internal fun homeComposerPrimaryAction(draft: String, canSend: Boolean, canSpeak: Boolean,
    showStop: Boolean, focusAllowed: Boolean, shellOwnsOrb: Boolean = false): HomeComposerAction = when {
    showStop -> HomeComposerAction.STOP
    !focusAllowed -> HomeComposerAction.NONE
    canSend && draft.isNotBlank() -> HomeComposerAction.SEND
    canSpeak && draft.isBlank() && !shellOwnsOrb -> HomeComposerAction.VOICE
    else -> HomeComposerAction.NONE
}
