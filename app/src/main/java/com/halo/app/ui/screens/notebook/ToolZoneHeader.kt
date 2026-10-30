package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Compact utility band. Editing gestures and durable action owners stay in their page. */
@Composable
internal fun ToolZoneHeader(title: String, onBack: () -> Unit, enabled: Boolean = true,
    onTitleClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density) > 0
    val stacked = density.fontScale >= 1.4f && !keyboard
    val back: @Composable () -> Unit = {
        if (!com.halo.app.ui.presence.LocalShellCarouselBackVisible.current) {
            val label = uiText("Back")
            TextButton(onClick = onBack, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) {
                Zone5Icon("action_back", HaloGlyph.PREVIOUS, Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp)); Text(label)
            }
        }
    }
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.heightIn(min = 48.dp).then(
            if (onTitleClick != null) Modifier.clickable(enabled = enabled, role = Role.Button,
                onClickLabel = uiText("Rename"), onClick = onTitleClick)
            else Modifier), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                maxLines = if (keyboard) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (stacked) Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            back(); Spacer(Modifier.weight(1f)); trailing()
        }
        identity(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        back()
        identity(Modifier.weight(1f))
        trailing()
    }
}

/** Only an explicitly identical enabled page action may replace this utility control. */
@Composable
internal fun ToolZoneHeaderAction(actionId: String, content: @Composable () -> Unit) {
    if (actionId !in com.halo.app.ui.presence.LocalShellCarouselActionIds.current) content()
}
