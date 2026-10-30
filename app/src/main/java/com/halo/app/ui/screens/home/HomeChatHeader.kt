package com.halo.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Conversation navigation stays above the thread; processing privacy stays in its existing shell owner. */
@Composable
internal fun HomeChatHeader(mode: String, modeEnabled: Boolean, onMode: () -> Unit,
    onHistory: () -> Unit, onNew: () -> Unit, onUpdates: () -> Unit,
    onActivity: () -> Unit, onClear: () -> Unit, onToday: (() -> Unit)? = null,
    modeMenu: (@Composable () -> Unit)? = null,
    surfaceSwitcher: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val shortScreen = LocalConfiguration.current.screenHeightDp < 480
    @Composable fun ModeControl() {
        Box {
            TextButton(onClick = onMode, enabled = modeEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(mode, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                HaloIcon(HaloGlyph.NEXT, null, Modifier.size(14.dp))
            }
            modeMenu?.invoke()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.halo_ic_history), uiText("Conversation history"), Modifier.size(24.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (shortScreen) ModeControl()
                else if (surfaceSwitcher != null) surfaceSwitcher()
                else Image(painterResource(if (MaterialTheme.colorScheme.background.luminance() < .5f)
                    R.drawable.halo_wordmark_dark else R.drawable.halo_wordmark_light),
                    uiText("halo"), Modifier.width(64.dp).height(28.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            }
            IconButton(onClick = onNew, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.halo_ic_new_chat), uiText("New conversation"), Modifier.size(24.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.halo_ic_more), stringResource(R.string.home_chat_options), Modifier.size(24.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (shortScreen && surfaceSwitcher != null) Box(Modifier.padding(8.dp)) { surfaceSwitcher() }
                    DropdownMenuItem(text = { Text(uiText("Updates")) }, onClick = { menu = false; onUpdates() })
                    onToday?.let { action ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_your_day)) }, onClick = { menu = false; action() })
                    }
                    DropdownMenuItem(text = { Text(uiText("Open Activity")) }, onClick = { menu = false; onActivity() })
                    DropdownMenuItem(text = { Text(uiText("Clear conversation")) }, onClick = { menu = false; onClear() })
                }
            }
        }
        if (!shortScreen) Box(Modifier.align(Alignment.CenterHorizontally)) { ModeControl() }
    }
}
