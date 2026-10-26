package com.halo.app.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Presentation only. The caller retains layout, note, voice, navigation and approval ownership. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenWorkspaceChrome(hasLayout: Boolean, editing: Boolean, fullScreen: Boolean,
    chromeVisible: Boolean, orbActive: Boolean, interactionAllowed: Boolean,
    onChat: () -> Unit, onSettings: () -> Unit, onFullScreen: (Boolean) -> Unit,
    onEdit: () -> Unit, onApply: () -> Unit, onCancel: () -> Unit, onAppearance: () -> Unit,
    onAdd: () -> Unit, onNote: () -> Unit, onTalk: () -> Unit,
    orbControl: (@Composable (Dp) -> Unit)?, shellZone: Boolean = false) {
    if (shellZone) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSurfaceSwitch(true, { if (!it) onChat() }, compact = true)
            if (editing) {
                TextButton(onClick = onApply, enabled = interactionAllowed, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply")) }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
            }
            if (fullScreen) TextButton(onClick = { onFullScreen(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.screen_exit_ambient))
            }
        }
        return
    }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    Column(Modifier.fillMaxWidth()) {
        if (!fullScreen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeSurfaceSwitch(true, { if (!it) onChat() }, Modifier.weight(1f), compact = LocalDensity.current.fontScale >= 1.4f)
            IconButton(onClick = onSettings, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) {
                HaloIcon(HaloGlyph.SETTINGS, uiText("Screen settings"))
            }
            if (!shellOwnsOrb) {
                if (orbControl != null && hasLayout) orbControl(48.dp)
                else if (orbControl == null) IconButton(onClick = onTalk, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.LISTEN, uiText("Talk to halo")) }
            }
        }
        if (hasLayout) {
            if (editing) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Preview"), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onApply, enabled = interactionAllowed, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply")) }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
                IconButton(onClick = onAppearance, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.APPEARANCE, stringResource(R.string.screen_appearance)) }
                IconButton(onClick = onAdd, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) { Icon(painterResource(R.drawable.halo_ic_add), uiText("Add card"), Modifier.size(24.dp)) }
            } else if (!fullScreen) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, enabled = interactionAllowed, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Customize")) }
                TextButton(onClick = { onFullScreen(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_ambient)) }
                IconButton(onClick = onNote, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.NOTE, stringResource(R.string.screen_new_note)) }
            }
        }
        if (fullScreen) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onFullScreen(false) }, modifier = Modifier.widthIn(max = 280.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.screen_exit_ambient)) }
            if (hasLayout && (chromeVisible || orbActive)) {
                IconButton(onClick = { onFullScreen(false); onSettings() }, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.SECURITY, uiText("Screen settings")) }
                if (!shellOwnsOrb) {
                    if (orbControl != null) orbControl(48.dp)
                    else TextButton(onClick = onTalk, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Talk to halo")) }
                }
                IconButton(onClick = { onFullScreen(false); onNote() }, enabled = interactionAllowed, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.NOTE, stringResource(R.string.screen_new_note)) }
            }
        }
    }
}
