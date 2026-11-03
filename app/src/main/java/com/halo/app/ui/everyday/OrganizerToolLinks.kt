package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.shell.ShellOrbCatalog

/** Working instruments live under the Organizer; source management is its settings. */
@Composable
internal fun OrganizerToolLinks(kids: Boolean, onOpen: (String) -> Unit) {
    val destinations = ShellOrbCatalog.organizerDestinations(kids)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Tools"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        destinations.filter { it.route != EverydayRoutes.SOURCES }.forEach { destination ->
            OutlinedButton({ onOpen(destination.route) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText(destination.title))
            }
        }
        destinations.firstOrNull { it.route == EverydayRoutes.SOURCES }?.let { source ->
            Text(uiText("Settings"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            OutlinedButton({ onOpen(source.route) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(source.title)) }
        }
    }
}
